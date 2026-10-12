"""Freeze Android release artifacts and their checkout/lock/tool provenance."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tomllib
import zipfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(*arguments):
    return subprocess.check_output([str(argument) for argument in arguments], text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-root", type=Path, required=True)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--full-elf", type=Path)
    parser.add_argument("--debug-map", type=Path, required=True)
    parser.add_argument("--zipper", type=Path, required=True)
    parser.add_argument("--tools", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--abi", default="arm64-v8a")
    parser.add_argument("--build-command", required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("output already exists; choose a new variant directory to preserve evidence")
    args.output.mkdir(parents=True)
    artifacts = {"aar": args.output / "capture.aar",
                 "debug_map": args.output / "capture.debug.gz", "native": args.output / "capture.so",
                 "native_zip": args.output / "capture.so.zip", "debug_elf": args.output / "capture.debug.elf"}
    if args.full_elf:
        artifacts["full_elf"] = args.output / "capture.full.so"
    for source, key in ((args.aar, "aar"), (args.full_elf, "full_elf"), (args.debug_map, "debug_map")):
        if source:
            shutil.copy2(source, artifacts[key])
    entry_name = f"jni/{args.abi}/libcapture.so"
    with zipfile.ZipFile(artifacts["aar"]) as archive:
        artifacts["native"].write_bytes(archive.read(entry_name))
    artifacts["debug_elf"].write_bytes(gzip.decompress(artifacts["debug_map"].read_bytes()))
    subprocess.run([str(args.zipper), "cC", str(artifacts["native_zip"]),
                    f"{entry_name}={artifacts['native']}"], check=True)
    lock = tomllib.loads((args.sdk_root / "Cargo.lock").read_text())
    packages = lock["package"]
    source_revisions = sorted({package["source"] for package in packages if
                              any(repo in package.get("source", "") for repo in
                                  ("shared-core", "rust-protobuf"))})
    ndk_properties = next((parent / "source.properties" for parent in args.tools.resolve().parents
                           if (parent / "source.properties").is_file()), None)
    if ndk_properties is None:
        parser.error("LLVM tools must come from an NDK with source.properties")
    rust_version = re.search(r'^RUST_VERSION = "([^"]+)"', (args.sdk_root / "MODULE.bazel").read_text(), re.M)
    if rust_version is None:
        parser.error("cannot find the registered RUST_VERSION in MODULE.bazel")
    snapshots = {}
    for name in ("Cargo.toml", "Cargo.lock", "MODULE.bazel", "MODULE.bazel.lock", ".bazelrc", ".bazelversion"):
        source = args.sdk_root / name
        if source.exists():
            shutil.copy2(source, args.output / source.name)
            snapshots[name] = digest(source)
    provenance = dict(
        schema_version=1, sdk_revision=command("git", "-C", args.sdk_root, "rev-parse", "HEAD"),
        checkout_status=command("git", "-C", args.sdk_root, "status", "--short"),
        dependency_sources=source_revisions, snapshot_sha256=snapshots, abi=args.abi,
        build_command=args.build_command.replace(str(args.sdk_root.resolve()), "."), host=command("uname", "-sm"),
        xcode=command("xcodebuild", "-version"), llvm=command(args.tools / "llvm-nm", "--version"),
        rust_version=rust_version[1], bazel_pin=(args.sdk_root / ".bazelversion").read_text().strip(),
        ndk_source_properties=ndk_properties.read_text(),
        ndk_source_properties_sha256=digest(ndk_properties),
        tool_sha256={name: digest(args.tools / name) for name in ("llvm-nm", "llvm-objdump", "clang")},
        zipper_sha256=digest(args.zipper),
        package_identities=sorted(f"{package['name']}@{package['version']}" for package in packages),
        artifacts={key: dict(filename=path.name, bytes=path.stat().st_size, sha256=digest(path))
                   for key, path in artifacts.items()},
    )
    (args.output / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(json.dumps({key: value["bytes"] for key, value in provenance["artifacts"].items()}, indent=2))


if __name__ == "__main__":
    main()
