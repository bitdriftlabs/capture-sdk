#!/usr/bin/env python3
"""Run every scenario in order and print one comparable table.

  python3 sweep.py --out /tmp/sweep [--serial S] [--only T01,T05] [--app gradle-test-app]

`run_scenario.py` runs one scenario; a sweep is 16 of them, and doing that by hand loses the two
things that make the results trustworthy:

**Ordering.** Screen-off scenarios go last. On a device that re-locks quickly, one early screen-off
invalidates every run after it — and `run_scenario.py` will dutifully skip each one, so the sweep
looks like it ran when it didn't. Putting them last confines that damage to the tail. Check
`lock_screen_lock_after_timeout` before assuming you're safe either way.

**Comparability.** Each scenario resets device state on the way out, but a sweep also needs the
results side by side to be readable at all: 16 separate summaries is not a finding, one table is.
Anything that looks off in the table gets read from that scenario's own `summary.txt` and raw
`logcat.txt`, which are the artifacts that cannot be wrong.

A scenario that raises is recorded as ERROR and the sweep continues — one bad scenario must not cost
the other fifteen.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import adbctl  # noqa: E402
import run_scenario  # noqa: E402

SCENARIOS = os.path.normpath(os.path.join(HERE, "..", "assets", "scenarios"))


def discover() -> list[str]:
    out = []
    for d in (os.path.join(SCENARIOS, "matrix"), SCENARIOS):
        if os.path.isdir(d):
            out += [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.endswith(".json")]
    return out


def uses_screen_off(path: str) -> bool:
    try:
        sc = json.load(open(path))
    except Exception:
        return False
    return any(s.get("action") == "screen-off" for s in sc.get("steps", []))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--serial")
    ap.add_argument("--app", default=adbctl.DEFAULT_APP, choices=list(adbctl.APPS))
    ap.add_argument("--only", help="comma-separated substrings; run just the matching scenarios")
    args = ap.parse_args()

    serial = args.serial or (adbctl.devices() or [None])[0]
    if not serial:
        sys.exit("no devices connected")
    pkg, _, _ = adbctl.APPS[args.app]

    paths = discover()
    if args.only:
        want = [w.strip() for w in args.only.split(",") if w.strip()]
        paths = [p for p in paths if any(w in os.path.basename(p) for w in want)]
    # Screen-off last, otherwise alphabetical within each half.
    paths.sort(key=lambda p: (uses_screen_off(p), os.path.basename(p)))
    if not paths:
        sys.exit("no scenarios matched")

    os.makedirs(args.out, exist_ok=True)
    print(f"[{serial}] {len(paths)} scenario(s); screen-off last\n")

    results = []
    for i, p in enumerate(paths, 1):
        name = os.path.basename(p).replace(".json", "")
        print(f"({i}/{len(paths)}) {name} …", flush=True)
        try:
            sc = json.load(open(p))
            summ = run_scenario.run_on(serial, sc, os.path.join(args.out, name), args.app)
            results.append((name, summ, None))
        except Exception as e:
            traceback.print_exc()
            results.append((name, None, str(e)))
        finally:
            # Belt and braces: run_on resets on its way out, but a raise mid-scenario can leave a
            # mode applied, and the next scenario would then measure the wrong device.
            try:
                adbctl.mode(serial, "reset", False, pkg)
            except Exception:
                pass

    print(f"\n{'scenario':<30} {'ref':<5} {'BG disk':<8} {'upload':<12} {'ack':>8}  {'netcut':>8}")
    print("-" * 82)
    for name, s, err in results:
        if err:
            print(f"{name:<30} ERROR  {err[:40]}")
            continue
        if s.get("skipped"):
            print(f"{name:<30} SKIPPED  {s['skipped']}")
            continue
        if s.get("no_reference"):
            # The reference never fired, so there is no foreground/background split to report and
            # any BG number would be foreground work mislabelled. For `recents` this absence IS the
            # expected result, not a failure.
            print(f"{name:<30} {'NONE':<5} (no reference event — see summary.txt)")
            continue
        bg = s["bg"]
        ack = f"{bg['ack_ms']}ms" if bg["ack_ms"] is not None else "-"
        cut = f"{s['net_cutoff_s']}s" if s.get("net_cutoff_s") else "-"
        flag = " *INVALID*" if s.get("invalid_run") else ""
        # A flush-path DEBO means this scenario measured the minimum-upload-interval floor instead of
        # the question it was written to ask. That used to be impossible to miss because the floor was
        # armed at launch and the fix was a long wait; now the waits are short precisely because the
        # floor is normally unarmed. If it ever gets armed again -- the workflow trigger returning, or
        # any new flush-path upload near startup -- every scenario silently starts measuring the gate.
        # One quiet column among 15 rows is not enough warning for that.
        if bg["upload"] == "DEBO":
            flag += "  <<< measured the upload floor, NOT the scenario"
        # Distinguishes "upload blocked" (a finding) from "ack had not arrived yet"
        # (inconclusive). Without this the two are identical in the table, and a slow-ack window
        # reads as a wall of upload regressions.
        if s.get("startup_upload_in_flight"):
            flag += "  <<< startup upload in flight: missing ack is INCONCLUSIVE"
        print(f"{name:<30} {'ok':<5} {'YES' if bg['snapshot_written'] else 'NO':<8} "
              f"{bg['upload'] + '/' + bg['upload_result']:<12} {ack:>8}  {cut:>8}{flag}")

    db = [(n, s["debounce"]) for n, s, e in results if s and s.get("debounce", {}).get("present")]
    if db:
        tot_c = sum(d["closed_coalesced"] for _, d in db)
        tot_o = sum(d["opened"] for _, d in db)
        print(f"\ndisk-flush debounce across sweep: {tot_o} window(s) opened, {tot_c} coalesced")
    inflight = [n for n, s, e in results if s and s.get("startup_upload_in_flight")]
    if inflight:
        print(f"\n  *** {len(inflight)} scenario(s) had the startup upload still in flight at"
              f" backgrounding:\n      {', '.join(inflight)}\n      Their missing acks are"
              f" inconclusive, not blocked uploads. Raise the pre-backgrounding wait and re-run"
              f" those.")
    debo = [n for n, s, e in results if s and s.get("bg", {}).get("upload") == "DEBO"]
    if debo:
        print(f"\n  *** {len(debo)} scenario(s) hit the minimum-upload-interval floor: "
              f"{', '.join(debo)}\n      Those runs measured the gate, not their subject. The floor is"
              f" armed only by a flush-path\n      upload; if one is now happening near startup, the"
              f" short foreground waits are no longer\n      safe and need raising back. See"
              f" references/flush-matrix.md.")
    print(f"\nper-scenario artifacts in {args.out}/<scenario>/{serial}/")


if __name__ == "__main__":
    main()
