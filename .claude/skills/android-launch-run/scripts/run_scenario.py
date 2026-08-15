#!/usr/bin/env python3
"""Run a declarative scenario across every connected device, then parse each capture.

  python3 run_scenario.py <scenario.json> --out /tmp/run1 [--serial S] [--sequential]

Per device it writes <out>/<serial>/{logcat.txt, summary.txt, state.json}.

Parallel across devices by default. Pass --sequential when a sub-second measurement has to be
trustworthy: concurrent adb round-trips and logcat streams add tens of ms of jitter, which is fine
for pass/fail but can distort an ack latency in the hundreds of ms.
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import json
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import adbctl  # noqa: E402
import parse_logs  # noqa: E402


def firewall_revoke(serial: str, uid: str, after_ms: int) -> int | None:
    """Device-clock ms at which the background firewall chain revoked network for uid.

    Read from the netpolicy *event log*, whose timestamps share the device clock with logcat, so
    the two can be compared directly. (The current state lives in `effective=` instead.)
    """
    best = None
    for ln in adbctl.adb(serial, "dumpsys netpolicy").splitlines():
        m = re.search(r"(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d):(\d\d\d) - "
                      r"Firewall rule changed: " + uid + r"-background-(\w+)", ln)
        if not m:
            continue
        _, mo, dd, hh, mi, ss, ms, st = m.groups()
        t = ((((int(mo) - 1) * 31 + int(dd)) * 24 + int(hh)) * 3600
             + int(mi) * 60 + int(ss)) * 1000 + int(ms)
        if st == "default" and t >= after_ms - 500 and (best is None or t < best):
            best = t
    return best


def run_on(serial: str, sc: dict, outdir: str, app: str) -> dict:
    pkg, activity, _ = adbctl.APPS[app]
    d = os.path.join(outdir, serial)
    os.makedirs(d, exist_ok=True)
    logf = os.path.join(d, "logcat.txt")

    # A locked device can only produce invalid runs, but that is this device's problem — it must
    # not abort the whole sweep. Skip it and let the other devices continue.
    if adbctl.is_locked(serial):
        print(f"[{serial}] SKIPPED — device is LOCKED; the app would launch behind the keyguard "
              f"and background itself, so any result would be invalid.", flush=True)
        return {"skipped": "device locked", "device": {"serial": serial,
                "api": adbctl.api_level(serial)}, "invalid_run": False,
                "fg": {}, "bg": {}, "net_cutoff_s": None}

    adbctl.mode(serial, "reset", False, pkg)
    adbctl.adb(serial, f"am force-stop {pkg}")
    if sc.get("rust_log"):
        adbctl.adb(serial, f"setprop debug.bitdrift.internal_rust_log '{sc['rust_log']}'")
    adbctl.adb(serial, "svc power stayon true")

    for m in sc.get("modes_before_launch", []):
        adbctl.mode(serial, m, True, pkg)

    # Capture both buffers: app + Rust logs live in main, OS lifecycle in events.
    adbctl.adb(serial, "logcat -c -b all")
    fh = open(logf, "w")
    cap = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "threadtime", "-b", "main,events"],
                           stdout=fh, stderr=subprocess.DEVNULL)
    time.sleep(1)

    try:
        for step in sc["steps"]:
            act = step["action"]
            if act == "wait":
                adbctl.action(serial, "wait", pkg, activity, step.get("seconds", 1))
            elif act == "mode":
                adbctl.mark(serial, f"ACTION mode {step['name']} on")
                adbctl.mode(serial, step["name"], True, pkg)
            else:
                adbctl.action(serial, act, pkg, activity)
    finally:
        adbctl.mark(serial, "ACTION observe-end")
        time.sleep(1)
        cap.terminate()
        try:
            cap.wait(timeout=5)
        except Exception:
            cap.kill()
        fh.close()

    evs = parse_logs.parse(logf)
    summ = parse_logs.summarize(evs, sc.get("reference_event", "process ON_STOP"))

    uid = adbctl.app_uid(serial, pkg)
    revoke = firewall_revoke(serial, uid, summ["ref_t"]) if uid else None
    summ["net_cutoff_s"] = round((revoke - summ["ref_t"]) / 1000.0, 3) if revoke else None
    summ["device"] = {"serial": serial, "api": adbctl.api_level(serial), "uid": uid,
                      "state_after": adbctl.state_line(serial, pkg)}

    with open(os.path.join(d, "state.json"), "w") as f:
        json.dump(summ, f, indent=2)

    # Human-readable timeline, same view parse_logs.py prints.
    import io, contextlib
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        sys.argv = ["parse_logs", logf, "--ref", sc.get("reference_event", "process ON_STOP")]
        try:
            parse_logs.main()
        except SystemExit:
            pass
    with open(os.path.join(d, "summary.txt"), "w") as f:
        f.write(buf.getvalue())

    adbctl.mode(serial, "reset", False, pkg)
    adbctl.adb(serial, "svc power stayon false")
    adbctl.adb(serial, f"am force-stop {pkg}")
    return summ


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("scenario")
    ap.add_argument("--out", required=True)
    ap.add_argument("--serial")
    ap.add_argument("--app", default=adbctl.DEFAULT_APP, choices=list(adbctl.APPS))
    ap.add_argument("--sequential", action="store_true")
    args = ap.parse_args()

    sc = json.load(open(args.scenario))
    targets = [args.serial] if args.serial else adbctl.devices()
    if not targets:
        sys.exit("no devices connected")
    os.makedirs(args.out, exist_ok=True)

    print(f"scenario '{sc['name']}' on {len(targets)} device(s)"
          f"{' sequentially' if args.sequential else ' in parallel'}")

    fn = lambda s: run_on(s, sc, args.out, args.app)
    if args.sequential or len(targets) == 1:
        results = {s: fn(s) for s in targets}
    else:
        with cf.ThreadPoolExecutor(max_workers=len(targets)) as ex:
            futs = {ex.submit(fn, s): s for s in targets}
            results = {futs[f]: f.result() for f in cf.as_completed(futs)}

    for s, r in results.items():
        if r.get("skipped"):
            print(f"\n[{s}] SKIPPED — {r['skipped']}")
            continue
        bg, fg = r["bg"], r["fg"]
        flag = "  *** INVALID (see summary.txt) ***" if r["invalid_run"] else ""
        print(f"\n[{s}] api={r['device']['api']}{flag}")
        print(f"  FG: {fg['snapshots']} snapshot(s), {fg['uploads_enqueued']} upload(s) "
              f"({fg['uploads_ok']} acked)")
        print(f"  BG: snapshot={'YES' if bg['snapshot_written'] else 'NO'} "
              f"platform_flush={bg['platform_flush']} upload={bg['upload']}/"
              f"{bg['upload_result']}"
              + (f" ack={bg['ack_ms']}ms" if bg["ack_ms"] is not None else ""))
        print(f"  net cutoff: {r['net_cutoff_s']}s after reference"
              if r["net_cutoff_s"] else "  net cutoff: not observed")
    print(f"\noutputs in {args.out}/<serial>/")


if __name__ == "__main__":
    main()
