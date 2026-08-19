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

# Wordings unique to one rev family, used to tell which binary actually produced a capture.
# The APK on the device is whatever was last built; switching branches does not reinstall it, so
# the tree and the binary can disagree — which is the most confusing state to debug from, because
# every signature lookup silently consults the wrong column of the reference tables.
REV_MARKERS = {
    "new-stats": [
        "flushing collected stats to disk",
        "prepared ",
        "started stats disk flush debounce window",
    ],
    "legacy-stats": [
        "received a signal to flush stats to disk",
        "sending pending flush upload",
        "stat flush upload attempt complete",
    ],
}

# Which family a pinned rev belongs to. Keyed by 8-char prefix. Deliberately an explicit table
# rather than a prefix test: an earlier version asked `pin.startswith("c3ba1cb")`, which silently
# reported the wrong family the moment the bump branch advanced to d8ac5975 — a tool that lies about
# staleness is worse than one that admits it doesn't know.
REV_FAMILY = {
    "c3ba1cba": "new-stats",    # bump / PR #1107, original tip
    "d8ac5975": "new-stats",    # bump, after the main merge; bd-client-stats byte-identical
    "5f0f7b29": "new-stats",    # main, after #1107 merged; wordings and flag defaults verified
                                # identical to d8ac5975 (only FlushTrigger::flush's signature changed)
    "42637e1f": "legacy-stats",  # main, before #1107
}


def infer_rev_family(path: str) -> tuple[str | None, dict[str, int]]:
    """Guess which shared-core rev family produced this capture, from message wording."""
    hits = {fam: 0 for fam in REV_MARKERS}
    with open(path, errors="replace") as fh:
        for raw in fh:
            for fam, marks in REV_MARKERS.items():
                if any(m in raw for m in marks):
                    hits[fam] += 1
    seen = [f for f, n in hits.items() if n]
    return (seen[0] if len(seen) == 1 else None), hits


def pinned_rev(start: str) -> str | None:
    """Short pinned rev from the nearest Cargo.toml.

    Walks up from the capture first, then from the cwd — captures are routinely written to a scratch
    dir outside the repo, and giving up in that case would silently skip the staleness check, which
    is the one check most worth not skipping.
    """
    for origin in (os.path.abspath(start), os.path.abspath(os.getcwd() + "/x")):
        d = origin
        for _ in range(10):
            d = os.path.dirname(d)
            if not d or d == "/":
                break
            f = os.path.join(d, "Cargo.toml")
            if os.path.exists(f):
                m = re.search(r'bd-client-common.*?rev = "([0-9a-f]{7,})"', open(f).read(), re.S)
                if m:
                    return m.group(1)[:8]
    return None


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
    print(f"recognised events: {len(evs)}")

    # Which binary produced this, and does it match the tree? Catching a stale APK here is the
    # difference between "the behaviour changed" and "I am testing last week's build".
    fam, hits = infer_rev_family(args.capture)
    pin = pinned_rev(args.capture)
    detail = " ".join(f"{f}={n}" for f, n in hits.items() if n) or "no rev markers found"
    print(f"binary rev family: {fam or 'INDETERMINATE'}  ({detail})")
    if pin:
        expected = REV_FAMILY.get(pin)
        print(f"Cargo.toml pins:   {pin}" + (f"  ({expected})" if expected else "  (rev not in REV_FAMILY)"))
        if expected is None:
            print(f"\n  NOTE: {pin} is not a rev this script knows about, so it cannot confirm the"
                  f"\n  install is current. Add it to REV_FAMILY in check_signatures.py once you know"
                  f"\n  which wording family it emits — and rebuild before trusting a capture, since a"
                  f"\n  rev bump is exactly when the APK goes stale.")
        elif fam and fam != expected:
            print(f"\n  *** STALE INSTALL: the capture looks like {fam}, but the tree pins {pin} "
                  f"({expected}).\n      The APK is whatever was last built — switching branches or "
                  f"bumping a rev does not\n      reinstall it. Run `adbctl.py install` and re-capture; "
                  f"until then every signature\n      lookup is consulting the wrong rev.")
    print()

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
