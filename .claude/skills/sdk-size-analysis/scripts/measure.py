"""One-command setup, pristine measurement, recovery and publication (Python 3.11+)."""

import argparse
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import venv

if sys.version_info < (3, 11):
    sys.exit("Python 3.11+ is required")

from run_analysis import SCRIPTS, digest, require, unsigned_build, verify_artifact


ROOT = SCRIPTS.parents[3]
BASE = ROOT / ".tmp/sdk-size-analysis"


def execute(label, arguments, logs, cwd=ROOT, env=None, live=False):
    log = logs / f"{label}.log"
    print(f"{label}: running; log {log}", flush=True)
    with log.open("w") as stream:
        with subprocess.Popen([str(argument) for argument in arguments], cwd=cwd, env=env,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as process:
            try:
                for line in process.stdout:
                    stream.write(line)
                    stream.flush()
                    if live:
                        print(line, end="", flush=True)
                exit_code = process.wait()
            except BaseException:
                process.terminate()
                process.wait()
                raise
    if exit_code:
        print(log.read_text()[-6000:], file=sys.stderr)
        raise RuntimeError(f"{label} failed ({exit_code}); see {log}")
    print(f"{label}: PASS", flush=True)
    return log.read_text().strip()


def setup_python(logs):
    require(sys.version_info >= (3, 11), "Python 3.11+ is required")
    directory = BASE / "venv"
    python = directory / "bin/python"
    if not python.is_file():
        venv.EnvBuilder(with_pip=True).create(directory)
    requirements = SCRIPTS / "requirements.txt"
    pins = dict(line.split("==") for line in requirements.read_text().splitlines()
                if line and not line.startswith("#"))
    probe = ("import importlib.metadata,json; "
             "print(json.dumps({d.metadata['Name'].lower():d.version for d in importlib.metadata.distributions()}))")
    installed = json.loads(execute("python-packages", [python, "-c", probe], logs))
    if any(installed.get(name.lower()) != version for name, version in pins.items()):
        execute("python-install", [python, "-m", "pip", "install", "--no-input", "--disable-pip-version-check",
                                   "-r", requirements], logs)
    execute("analysis-tests", [python, "-m", "unittest", "discover", "-s", SCRIPTS,
                               "-p", "test_analysis.py", "-v"], logs)
    return python


def preflight(logs, requested_xcode=None):
    require(sys.platform == "darwin", "Android/iOS measurement requires macOS and full Xcode")
    for tool in ("git", "xcodebuild", "xcrun"):
        require(shutil.which(tool), f"missing required tool: {tool}")
    xcode = execute("xcode-version", ["xcodebuild", "-version"], logs)
    match = re.search(r"^Xcode (\S+)$", xcode, re.M)
    require(match, "cannot detect the selected Xcode version")
    require(not requested_xcode or requested_xcode == match[1],
            f"selected Xcode is {match[1]}, not {requested_xcode}; select the intended Xcode first")
    execute("apple-clang", ["xcrun", "--find", "clang"], logs)
    execute("apple-strip", ["xcrun", "--find", "strip"], logs)
    java_home = execute("jdk17-home", ["/usr/libexec/java_home", "-v", "17"], logs)
    java_version = execute("jdk17-version", [Path(java_home) / "bin/java", "-version"], logs)
    require(re.search(r'version "17[.\"]', java_version), "JDK 17 is required")
    env = dict(os.environ, JAVA_HOME=java_home)
    env["PATH"] = str(Path(java_home) / "bin") + os.pathsep + env.get("PATH", "")
    return match[1], env


def script_hashes():
    return {path.name: digest(path) for path in sorted(SCRIPTS.glob("*.py"))}


def recover_unsigned_probe(sdk, output):
    build = sdk / "examples/swift/hello_world/BUILD"
    backup = output / "ios/app.BUILD.original"
    if not backup.is_file() or build.read_bytes() == backup.read_bytes():
        return
    original = backup.read_bytes()
    committed = subprocess.check_output(["git", "-C", str(sdk), "show", "HEAD:examples/swift/hello_world/BUILD"])
    require(original == committed, "unsigned BUILD backup does not match the measured revision")
    require(build.read_text() == unsigned_build(original.decode()),
            "BUILD contains changes beyond the unsigned probe; refusing to overwrite them")
    build.write_bytes(original)
    print("Recovered interrupted unsigned BUILD probe", flush=True)


def completed_stage(output, platform, revision):
    receipt = output / f"run-provenance.{platform}.json"
    if not receipt.is_file():
        return False
    provenance = json.loads(receipt.read_text())
    require(provenance["sdk_revision"] == revision, f"{platform} revision changed")
    require(provenance["analysis_script_sha256"] == script_hashes(),
            "analysis scripts changed since collection; start a new run")
    if provenance.get("python"):
        require(provenance["python"]["requirements_sha256"] == digest(SCRIPTS / "requirements.txt"),
                "analysis requirements changed since collection; start a new run")
    require(provenance.get("artifact_sha256"), f"{platform} lacks a complete artifact receipt; start a new run")
    for name, expected in provenance["artifact_sha256"].items():
        verify_artifact(output / name, expected)
    return True


def archive_partial(output, platform):
    paths = [output / platform, output / "logs" / platform]
    if not any(path.exists() for path in paths):
        return
    failures = output / "failures"
    failures.mkdir(exist_ok=True)
    archive = Path(tempfile.mkdtemp(prefix=f"{platform}-", dir=failures))
    for path in paths:
        if path.exists():
            shutil.move(str(path), archive / ("artifacts" if path.name == platform and path.parent == output else "logs"))
    print(f"Preserved partial {platform} attempt in {archive}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", help="committed SDK revision (default: HEAD for a new run)")
    parser.add_argument("--run", type=Path, help="new or existing run directory under this checkout's .tmp/")
    parser.add_argument("--platform", choices=("all", "android", "ios"), default="all")
    parser.add_argument("--xcode-version", help="require the selected Xcode version")
    parser.add_argument("--report-prefix", type=Path, help="optional durable report destination")
    parser.add_argument("--baseline", type=Path, help="explicit baseline evidence for scripted deltas/control checks")
    parser.add_argument("--publish-only", action="store_true", help="audit and publish without builds")
    parser.add_argument("--check-only", action="store_true", help="check environment, dependencies, tests and doc links")
    args = parser.parse_args()
    require(not args.publish_only or args.run, "--publish-only requires --run")
    BASE.mkdir(parents=True, exist_ok=True)
    logs = Path(tempfile.mkdtemp(prefix="setup-", dir=BASE))
    python = setup_python(logs)
    if args.publish_only:
        env = os.environ.copy()
        xcode = None
    else:
        xcode, env = preflight(logs, args.xcode_version)
    if args.check_only:
        print(f"PASS: setup, preflight, tests and documentation checks; logs {logs}", flush=True)
        return
    run = args.run.resolve() if args.run else Path(tempfile.mkdtemp(prefix="run-", dir=BASE))
    require(run.is_relative_to((ROOT / ".tmp").resolve()), "--run must be inside this checkout's .tmp/")
    run.mkdir(parents=True, exist_ok=True)
    with (run / ".lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError(f"another measurement is using {run}") from None
        state_path = run / "run.json"
        if state_path.is_file():
            state = json.loads(state_path.read_text())
            require(Path(state["sdk_root"]).resolve() == run / "sdk" and
                    Path(state["output"]).resolve() == run / "results", "run state paths differ")
            require(not xcode or xcode == state["xcode_version"], "selected Xcode changed; start a new run")
            if args.revision:
                revision = execute("requested-revision", ["git", "rev-parse", "--verify", "--end-of-options",
                                                          f"{args.revision}^{{commit}}"], logs)
                require(revision == state["sdk_revision"], "requested revision changed; start a new run")
        else:
            require(not args.publish_only, "run state is missing; use the lower-level runner for legacy runs")
            require(not (run / "sdk").exists() and not (run / "results").exists(), "run directory is already in use")
            revision = execute("sdk-revision", ["git", "rev-parse", "--verify", "--end-of-options",
                                               f"{args.revision or 'HEAD'}^{{commit}}"], logs)
            state = dict(schema_version=1, sdk_root=str(run / "sdk"), output=str(run / "results"),
                         sdk_revision=revision, xcode_version=xcode,
                         created_at=datetime.now(timezone.utc).isoformat())
            state_path.write_text(json.dumps(state, indent=2) + "\n")
        sdk, output = Path(state["sdk_root"]), Path(state["output"])
        for name in ("report_prefix", "baseline"):
            value = getattr(args, name)
            if value:
                state[name] = str(value.resolve())
        state_path.write_text(json.dumps(state, indent=2) + "\n")
        if not sdk.exists():
            require(not args.publish_only, "measured worktree is missing")
            execute("sdk-worktree", ["git", "worktree", "add", "--detach", sdk, state["sdk_revision"]], logs)
        require(execute("measured-revision", ["git", "-C", sdk, "rev-parse", "HEAD"], logs) == state["sdk_revision"],
                "measured checkout revision changed")
        if not args.publish_only:
            recover_unsigned_probe(sdk, output)
            require(not execute("measured-status", ["git", "-C", sdk, "status", "--porcelain"], logs),
                    "measured checkout has user edits; preserve it and start a new run")
        command = [python, SCRIPTS / "run_analysis.py", "--sdk-root", sdk, "--output", output,
                   "--xcode-version", state["xcode_version"]]
        print(f"Run: {run}\nResume: python3 {SCRIPTS / 'measure.py'} --run {run}", flush=True)
        if not args.publish_only:
            platforms = ("android", "ios") if args.platform == "all" else (args.platform,)
            if "ios" in platforms and "android" not in platforms:
                require(completed_stage(output, "android", state["sdk_revision"]),
                        "iOS requires a completed Android stage; run --platform android first or omit --platform")
            for platform in platforms:
                if completed_stage(output, platform, state["sdk_revision"]):
                    print(f"{platform}: retained artifacts verified; skipping collection", flush=True)
                    continue
                archive_partial(output, platform)
                execute(f"measure-{platform}", [*command, "--platform", platform], logs, env=env, live=True)
        if args.publish_only or all((output / f"run-provenance.{platform}.json").is_file()
                                    for platform in ("android", "ios")):
            publication = [*command, "--publish-only"]
            if state.get("report_prefix"):
                publication.extend(["--report-prefix", state["report_prefix"]])
            if state.get("baseline"):
                publication.extend(["--baseline", state["baseline"]])
            execute("publish", publication, logs, env=env)
            print((logs / "publish.log").read_text(), end="", flush=True)
        print(f"PASS: {run}; setup/stage logs {logs}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
