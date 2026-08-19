#!/usr/bin/env python3
"""Deliberately land two stats flushes inside the disk-flush debounce window.

  python3 force_coalesce.py --out /tmp/coalesce [--serial S] [--attempts 3]

Why this exists: the debounce window (`stats.disk_flush_debounce_ms`, 1s) is meant to coalesce a
second flush arriving inside it into a single disk write. Across every ordinary run the window has
opened and closed *empty* — 36 windows in the original matrix study, 13 more since, zero coalesces —
so the coalescing branch has never actually executed. Ordinary scenarios can't reach it: the two
flush sources that are easy to trigger land 3s apart, which is outside the window.

The trick is that the periodic disk flush is almost perfectly predictable. It is anchored at the
first flush of the process (~`stats.first_upload_flush_interval_ms` after launch) and repeats every
`stats.disk_flush_interval_ms`. A platform flush, by contrast, is triggered on demand but arrives
late by a fixed amount: HOME -> `process ON_STOP` is a ~1.35s `ProcessLifecycleOwner` debounce, then
the flush follows within ~20ms.

So rather than react to a tick (too late — the reaction itself costs more than the window), observe
one tick and *pre-empt the next*: press HOME at `tick + period - lead`, where
`lead = home_to_flush - target_offset`. The forced flush then lands `target_offset` into the window
the next tick opens, and should be coalesced instead of writing again.

Logcat streaming latency makes the observed tick slightly late, which pushes the forced flush
slightly *deeper* into the window rather than out of it — the error is in the safe direction. The
default target of 0.35s leaves room for both that and for `am start` jitter.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import adbctl  # noqa: E402
import parse_logs  # noqa: E402

WRITE = re.compile(r"bd_client_stats\S*: writing snapshot:")
OPEN = re.compile(r"bd_client_stats\S*: started stats disk flush debounce window")
RUST_LOG = "info,bd_client_stats=debug,bd_logger=debug"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--serial")
    ap.add_argument("--app", default=adbctl.DEFAULT_APP, choices=list(adbctl.APPS))
    ap.add_argument("--attempts", type=int, default=3)
    ap.add_argument("--period", type=float, default=30.0,
                    help="disk flush interval in seconds (read it off the runtime config)")
    ap.add_argument("--home-to-flush", type=float, default=1.36,
                    help="measured HOME -> forced-flush latency; ON_STOP debounce plus a little")
    ap.add_argument("--target-offset", type=float, default=0.35,
                    help="where inside the window to aim the forced flush")
    args = ap.parse_args()

    serial = args.serial or (adbctl.devices() or [None])[0]
    if not serial:
        sys.exit("no devices connected")
    pkg, activity, _ = adbctl.APPS[args.app]
    adbctl.require_unlocked(serial)

    os.makedirs(args.out, exist_ok=True)
    logf = os.path.join(args.out, "logcat.txt")

    adbctl.mode(serial, "reset", False, pkg)
    adbctl.adb(serial, f"am force-stop {pkg}")
    adbctl.adb(serial, f"setprop debug.bitdrift.internal_rust_log '{RUST_LOG}'")
    adbctl.adb(serial, "svc power stayon true")
    adbctl.adb(serial, "logcat -c -b all")

    fh = open(logf, "w")
    cap = subprocess.Popen(
        ["adb", "-s", serial, "logcat", "-v", "threadtime", "-b", "main,events"],
        stdout=fh, stderr=subprocess.DEVNULL)
    time.sleep(1)

    lead = args.home_to_flush - args.target_offset
    print(f"[{serial}] period={args.period}s  lead={lead:.2f}s  "
          f"aiming {args.target_offset:.2f}s into the window")

    try:
        adbctl.action(serial, "launch", pkg, activity)
        reader = open(logf, "r", errors="replace")
        attempts = 0
        deadline = time.time() + 60 + args.attempts * (args.period + 12)

        while attempts < args.attempts and time.time() < deadline:
            line = reader.readline()
            if not line:
                time.sleep(0.02)
                continue
            if not WRITE.search(line):
                continue

            # A tick just landed (host-side). Pre-empt the *next* one.
            observed = time.time()
            sleep_for = args.period - lead
            if sleep_for <= 0:
                continue
            attempts += 1
            adbctl.mark(serial, f"COALESCE attempt {attempts}: HOME in {sleep_for:.2f}s")
            print(f"  attempt {attempts}: tick observed, HOME in {sleep_for:.2f}s")

            # Drain so the next readline() sees only new lines.
            while reader.readline():
                pass
            time.sleep(max(0.0, sleep_for - (time.time() - observed)))
            adbctl.action(serial, "home", pkg, activity)

            # Let the window open, coalesce or not, and close.
            time.sleep(4)
            if attempts < args.attempts:
                adbctl.action(serial, "launch", pkg, activity)
                time.sleep(2)
                while reader.readline():
                    pass
    finally:
        adbctl.mark(serial, "ACTION observe-end")
        time.sleep(1.5)
        cap.terminate()
        try:
            cap.wait(timeout=5)
        except Exception:
            cap.kill()
        fh.close()
        adbctl.mode(serial, "reset", False, pkg)
        adbctl.adb(serial, "svc power stayon false")
        adbctl.adb(serial, f"am force-stop {pkg}")

    evs = parse_logs.parse(logf, pkg)
    writes = [e for e in evs if e["kind"] == "SNAP"]
    opens = [e for e in evs if e["kind"] == "DEBOUNCE_OPEN"]
    shuts = [e for e in evs if e["kind"] == "DEBOUNCE_SHUT"]
    coal = [e for e in evs if e["kind"] == "DEBOUNCE_COALESCE"]

    print(f"\nwrites={len(writes)}  windows opened={len(opens)}  "
          f"closed empty={len(shuts)}  COALESCED={len(coal)}")
    gaps = [round((b["t"] - a["t"]) / 1000.0, 3)
            for a, b in zip(writes, writes[1:])]
    print(f"consecutive write gaps (s): {gaps}")
    if coal:
        print("\nCOALESCE OBSERVED — the debounce branch executed:")
        for e in coal:
            print(f"  {e['ts']}  {e['label']}")
    else:
        near = [g for g in gaps if g < 1.0]
        print("\nno coalesce. Closest approach:"
              f" {min(gaps) if gaps else 'n/a'}s between writes")
        if near:
            print("  Two writes landed under 1s apart WITHOUT a coalesce line — that would be an"
                  "\n  invariant violation worth reporting, not a tuning problem.")
        else:
            print("  Every flush landed outside the window. Nudge --target-offset or re-measure"
                  "\n  --home-to-flush against a real run before concluding anything.")
    print(f"\ncapture: {logf}")


if __name__ == "__main__":
    main()
