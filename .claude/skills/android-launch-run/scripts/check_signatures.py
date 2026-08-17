#!/usr/bin/env python3
"""Audit a capture against the parser's patterns before you trust a negative result.

  python3 check_signatures.py <capture.txt> [--pkg io.bitdrift.gradletestapp]

The failure mode this exists to prevent: a shared-core bump renames a log message, the matching
pattern in parse_logs.py silently stops firing, and the summary reports "no upload happened" when
the truth is "I can no longer see uploads". Those two look identical downstream, and both a false
regression report and a false pass have already come from exactly this.

So: rather than asking "did the behaviour happen?", ask "can I still see this class of event at
all?". Two answers come out of that:

  SEEN     the pattern matched — a zero count elsewhere is a real absence
  UNSEEN   the pattern never matched anywhere in the capture — a zero count proves nothing

Then every bd_* line the parser did NOT recognise is listed, normalised and grouped. A renamed
message shows up there immediately, which turns "why is the summary empty?" into a two-second read.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import parse_logs  # noqa: E402

# Families worth reporting together: a zero for the whole family is what matters, not per-pattern.
FAMILIES = {
    "lifecycle": ["PROC", "FOCUS"],
    "os-lifecycle": ["OS_STOP", "OS_PAUSE", "OS_FOCUS", "OS_FREEZE"],
    "flush-attribution": ["PLATFORM_FLUSH", "BLOCK_OK", "BLOCK_ERR"],
    "stats-flush": ["FORCED", "TICK", "MERGE", "SNAP", "FLUSHED"],
    "stats-upload": ["PREP", "ENQ", "DISPATCH", "ACK", "RES"],
    "stats-gating": ["DEBO", "DROPPED"],
    "stats-debounce": ["DEBOUNCE_OPEN", "DEBOUNCE_SHUT", "DEBOUNCE_COALESCE"],
    "transport": ["STREAM_UP", "STREAM_DOWN"],
    "buffers": ["BUF", "LOGBATCH"],
}

NORMALISE = [
    (re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "<uuid>"),
    (re.compile(r"\b\d+\b"), "N"),
]


def normalise(msg: str) -> str:
    for rx, rep in NORMALISE:
        msg = rx.sub(rep, msg)
    return msg


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("capture")
    ap.add_argument("--pkg", default="io.bitdrift.gradletestapp")
    ap.add_argument("--quiet-unmatched", action="store_true",
                    help="skip the unrecognised-line listing")
    args = ap.parse_args()

    evs = parse_logs.parse(args.capture, args.pkg)
    counts = Counter(e["kind"] for e in evs)

    print(f"capture: {args.capture}")
    print(f"recognised events: {len(evs)}\n")

    unseen_families = []
    for fam, kinds in FAMILIES.items():
        total = sum(counts.get(k, 0) for k in kinds)
        detail = " ".join(f"{k}={counts.get(k, 0)}" for k in kinds)
        status = "SEEN  " if total else "UNSEEN"
        if not total:
            unseen_families.append(fam)
        print(f"  {status} {fam:<20} {detail}")

    # Any bd_* line the parser didn't claim. A renamed message lands here the moment it changes.
    unmatched: Counter = Counter()
    claimed = {(e["ts"], e["pid"]) for e in evs}
    with open(args.capture, errors="replace") as fh:
        for raw in fh:
            m = parse_logs.LINE.match(raw.rstrip("\n"))
            if not m:
                continue
            tag = m["tag"].strip()
            if not tag.startswith("bd_"):
                continue
            if (m["ts"], m["pid"]) in claimed:
                continue
            unmatched[f"{tag}: {normalise(m['msg'].strip())}"] += 1

    if unseen_families:
        print(f"\n  *** {len(unseen_families)} family/families never matched: "
              f"{', '.join(unseen_families)}")
        print("      A zero count for these proves nothing about behaviour — the detector itself")
        print("      may be dead. Check the unrecognised lines below for a renamed message before")
        print("      reporting any absence as a finding.")

    if unmatched and not args.quiet_unmatched:
        print(f"\nunrecognised bd_* messages ({len(unmatched)} distinct):")
        for msg, n in unmatched.most_common(40):
            print(f"  {n:4d}x  {msg}")
        if len(unmatched) > 40:
            print(f"  … and {len(unmatched) - 40} more")


if __name__ == "__main__":
    main()
