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

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import CELLS, EXT_DIR, PKG, TRIGGERS, adb, command_name  # noqa: E402


def mark(msg):
    adb("shell", "log -p i -t E2E-RUN " + shlex.quote(msg), check=False)
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def screen():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size", check=False))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


W, H = screen()
X, Y_LOW, Y_HIGH = str(W // 2), str(int(H * 0.72)), str(int(H * 0.38))  # swipe span scales with the screen


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml")).iter("node"))


def tap_text(pattern, scrolls=8):
    rx = re.compile(pattern, re.I)
    for _ in range(scrolls + 1):
        for n in nodes():
            if rx.search(n.get("text") or "") or rx.search(n.get("content-desc") or ""):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                mark(f"ACTION tap '{n.get('text') or n.get('content-desc')}'")
                return
        adb("shell", "input", "swipe", X, Y_LOW, X, Y_HIGH, "300")
        time.sleep(0.6)
    raise RuntimeError(f"no UI node matching {pattern!r}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cell", required=True)
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--out", required=True)
    ap.add_argument("--trigger", choices=sorted(TRIGGERS),
                    help="use another trigger button than the cell default, e.g. when a live workflow you may not "
                         "stop reacts to the default one. Pass the same value to make_workflow.py and run_cell.py")
    ap.add_argument("--settle", type=int, default=30, help="seconds for workflow config delivery after launch")
    ap.add_argument("--wait", type=int, help="seconds after the trigger for capture + upload")
    a = ap.parse_args()
    cell = CELLS[a.type]
    wait = a.wait or cell["wait"]
    out = pathlib.Path(a.out) / a.cell
    out.mkdir(parents=True, exist_ok=True)

    adb("shell", "rm", "-rf", EXT_DIR, check=False)
    adb("logcat", "-c", check=False)
    logcat = subprocess.Popen(["adb", "logcat", "-v", "threadtime", "-b", "main,events,system"],
                              stdout=open(out / "logcat.txt", "w"), stderr=subprocess.STDOUT)
    started = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    try:
        adb("shell", "am", "force-stop", PKG)
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        mark(f"CELL {a.cell} launch")
        adb("shell", "am", "start", "-W", "-n", f"{PKG}/.ui.activities.MainActivity")
        time.sleep(a.settle)
        tap_text(r"^SDK APIs$")
        time.sleep(1.5)
        trigger = a.trigger or cell["trigger"]
        mark(f"TRIGGER {trigger}")
        tap_text(f"^{re.escape(trigger)}$")
        t_end, i = time.time() + cell["activity"], 0
        while time.time() < t_end:  # allocations / CPU work while the capture records
            y1, y2 = (Y_LOW, Y_HIGH) if i % 2 == 0 else (Y_HIGH, Y_LOW)
            adb("shell", "input", "swipe", X, y1, X, y2, "200")
            i += 1
        mark(f"ACTIVITY done; waiting {wait}s for result + upload")
        time.sleep(wait)
    finally:
        mark(f"CELL {a.cell} end")
        time.sleep(1)
        logcat.terminate()

    log = (out / "logcat.txt").read_text(errors="replace")
    name = re.escape(command_name(a.type))
    info = {
        "cell": a.cell, "type": a.type, "started": started,
        "invocations": [{"args": x[0], "command_id": x[1], "session_id": x[2]} for x in re.findall(
            rf"Command {name} invoked with (\{{.*?\}}) \(commandId=([^,]+), sessionId=([^)]+)\)", log)],
        "profiling_result_lines": re.findall(r"Profiling result type=\S+ bytes=\d+", log),
        "profiling_failures": re.findall(r"Profiling failed .*", log),
        "traces": [],
    }
    for path, size, sid, cid in re.findall(r"E2E trace copy path=(\S+) bytes=(\d+) sessionId=(\S+) commandId=(\S+)", log):
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
