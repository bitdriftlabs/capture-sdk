#!/usr/bin/env python3
"""Run one cell on the connected Android device.

Launch the gradle-test-app, wait for workflow config, open the SDK APIs tab, tap the cell's trigger
button, scroll the UI while the capture records, wait for the upload, then pull the on-device copy
of the uploaded trace (written when the app is built with -Pe2eTraceCopy=true). Writes <out>/<cell>/cell.json and logcat.txt.

    run_cell.py --cell R-HPJ --type heap_profile_java --out .tmp/.../runs [--settle 30] [--wait 45]
"""
import argparse
import json
import pathlib
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import CELLS, EXT_DIR, PKG, STRICT_WORKFLOW_CELLS, TRIGGERS, adb, command_name  # noqa: E402


def mark(msg):
    adb("shell", "log -p i -t E2E-RUN " + shlex.quote(msg), check=False)
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def screen():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size", check=False))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml")).iter("node"))


def tap_text(pattern, scrolls=8):
    rx = re.compile(pattern, re.I)
    w, h = screen()
    for _ in range(scrolls + 1):
        for n in nodes():
            if rx.search(n.get("text") or "") or rx.search(n.get("content-desc") or ""):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                mark(f"ACTION tap '{n.get('text') or n.get('content-desc')}'")
                return
        adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.72)), str(int(h * 0.38)), "300")
        time.sleep(0.6)
    raise RuntimeError(f"no UI node matching {pattern!r}")


def await_action(action_id, timeout):
    """Wait for the deployed action in the real SDK's config cache (debug build only)."""
    deadline = time.monotonic() + timeout
    while True:
        result = subprocess.run(
            ["adb", "exec-out", "run-as", PKG, "cat", "files/bitdrift_capture/config/protobuf.pb"],
            capture_output=True, timeout=10,
        )
        if result.returncode == 0:
            try:
                if action_id.encode() in zlib.decompress(result.stdout):
                    return
            except zlib.error:
                pass  # The cache can be replaced while it is being read.
        if time.monotonic() >= deadline:
            raise RuntimeError(f"SDK config never contained action {action_id}; check key, endpoint, and workflow eligibility")
        time.sleep(2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cell", required=True)
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--out", required=True)
    ap.add_argument("--trigger", choices=sorted(TRIGGERS),
                    help="use another trigger button than the cell default, e.g. when a live workflow you may not "
                         "stop reacts to the default one. Pass the same value to make_workflow.py and run_cell.py")
    ap.add_argument("--settle", type=int, default=30, help="seconds for workflow config delivery after launch")
    ap.add_argument("--wait", type=int, help="seconds after activity for capture + upload")
    ap.add_argument("--already-open", action="store_true", help="keep the configured app open on SDK APIs")
    ap.add_argument("--await-action", help="wait for a read-back action ID in the debug SDK's config cache")
    ap.add_argument("--config-wait", type=int, default=120, help="seconds to wait with --await-action")
    a = ap.parse_args()
    cell = CELLS[a.type]
    wait = cell["wait"] if a.wait is None else a.wait
    w, h = screen()
    x, y_low, y_high = str(w // 2), str(int(h * 0.72)), str(int(h * 0.38))
    out = pathlib.Path(a.out) / a.cell
    out.mkdir(parents=True, exist_ok=True)

    with (out / "logcat.txt").open("w") as log_file:
        logcat = subprocess.Popen(["adb", "logcat", "-T", "1", "-v", "threadtime", "-b", "main,events,system"],
                                  stdout=log_file, stderr=subprocess.STDOUT)
    started = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    try:
        if not a.already_open:
            adb("shell", "am", "force-stop", PKG)
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            mark(f"CELL {a.cell} launch")
            adb("shell", "am", "start", "-W", "-n", f"{PKG}/.ui.activities.MainActivity")
            if not a.await_action:
                time.sleep(a.settle)
            tap_text(r"^SDK APIs$")
        if a.await_action:
            await_action(a.await_action, a.config_wait)
        time.sleep(1.5)
        trigger = a.trigger or cell["trigger"]
        mark(f"TRIGGER {trigger}")
        tap_text(f"^{re.escape(trigger)}$")
        if a.type in STRICT_WORKFLOW_CELLS:
            # The next event advances the command step; it must not retrigger the workflow.
            follow_up = "Log JSON Field" if trigger == "Log Single Message" else "Log Single Message"
            tap_text(f"^{re.escape(follow_up)}$")
        t_end, i = time.time() + cell["activity"], 0
        while time.time() < t_end:  # allocations / CPU work while the capture records
            y1, y2 = (y_low, y_high) if i % 2 == 0 else (y_high, y_low)
            adb("shell", "input", "swipe", x, y1, x, y2, "200")
            i += 1
        mark(f"ACTIVITY done; waiting {wait}s for result + upload")
        time.sleep(wait)
    finally:
        mark(f"CELL {a.cell} end")
        logcat.terminate()
        logcat.wait(timeout=5)

    log = (out / "logcat.txt").read_text(errors="replace").partition(f"TRIGGER {trigger}")[2]
    name = re.escape(command_name(a.type))
    info = {
        "cell": a.cell, "type": a.type, "started": started,
        "invocations": [{"args": x[0], "command_id": x[1], "session_id": x[2]} for x in re.findall(
            rf"Command {name} invoked with (\{{.*?\}}) \(commandId=([^,]+), sessionId=([^)]+)\)", log)],
        "profiling_result_lines": re.findall(r"Profiling result type=\S+ bytes=\d+", log),
        "profiling_failures": re.findall(r"Profiling failed .*", log),
        "traces": [],
    }
    copies = re.findall(r"E2E trace copy path=(\S+) bytes=(\d+) sessionId=(\S+) commandId=(\S+)", log)
    for path, size, sid, cid in copies if cell["device_copy"] else ():
        local = out / f"device_{cid}.perfetto-trace"
        adb("pull", path, str(local))
        info["traces"].append({"device_path": path, "bytes": int(size), "session_id": sid, "command_id": cid,
                               "local": str(local)})
    (out / "cell.json").write_text(json.dumps(info, indent=2))
    print(json.dumps({k: info[k] for k in ("invocations", "profiling_result_lines", "profiling_failures")}, indent=1))
    if cell["device_copy"]:
        print(f"device copies: {len(info['traces'])}")
    else:
        print("built-in command: no invocation log line and no on-device copy are expected; "
              "post_cell.py --workflow <id> finds the session")
    if cell["device_copy"] and not info["traces"]:
        sys.exit("no on-device trace copy: check the workflow is LIVE, SDK key/URL prefs, trigger match, "
                 "and that this build was made with -Pe2eTraceCopy=true")


if __name__ == "__main__":
    main()
