#!/usr/bin/env python3
"""Assert every log pattern still matches a real line. Run after any shared-core bump.

  python3 selftest.py [--capture <logcat.txt>]

This exists because the parser has been wrong four separate times -- the suppression pattern pinned
to the literal word "flush", a missing server-ack pattern, a dropped `no_reference` guard, and a
coalesce pattern matching a message that does not exist -- and every one was found by accident,
mid-investigation, after it had already produced a wrong answer. A pattern that stops matching does
not error; it silently reports "the behaviour did not happen". That is the single most expensive
failure mode this tooling has, and until now nothing guarded against it.

Each sample below is a real logcat line, timestamps and pids normalised, taken from captures on
physical hardware and an emulator. A few are marked SYNTHETIC: they come from paths adb cannot trigger
(the blocking flush only runs on JVM crash) or that need a flush collision, so they are written from
the documented wordings rather than witnessed. Treat those as weaker evidence.

Failure means one of two things, and the difference matters: either a shared-core bump renamed a
message -- update the pattern and add the new wording as a sample, keeping the old one -- or an edit
broke a pattern that was working. Both are worth failing a build over.
"""
from __future__ import annotations

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import parse_logs  # noqa: E402

SYNTHETIC = {"BLOCK_OK", "BLOCK_ERR", "DROPPED", "BUF"}

SAMPLES = {
    "MARK": "08-17 12:00:00.000  1000  1001 I ANDROID-RUN: ACTION launch",
    "OS_PAUSE": "08-17 12:00:00.000  1000  1001 I wm_on_paused_called: [218939535,com.google.android.apps.nexuslauncher.NexusLauncherActivity,performPause,0]",
    "OS_FOCUS": "08-17 12:00:00.000  1000  1001 I input_focus: ViewRootImpl focus=false for com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity",
    "PROC": "08-17 12:00:00.000  1000  1001 I bitdrift-lifecycle: process ON_CREATE",
    "FOCUS": "08-17 12:00:00.000  1000  1001 I bitdrift-lifecycle: window MainActivity focus=true",
    "OS_STOP": "08-17 12:00:00.000  1000  1001 I wm_on_stop_called: [218939535,com.google.android.apps.nexuslauncher.NexusLauncherActivity,STOP_ACTIVITY_ITEM,0]",
    "FORCED": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: flushing collected stats to disk",
    "MERGE": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: updating aggregated snapshot file with 30 metrics, 0 overflowed IDs, and 14 workflow debug entries",
    "SNAP": "08-17 12:00:00.000  1000  1001 D bd_client_stats::file_manager: writing snapshot: stats_uploads/816f592f-fd0f-4c00-993b-cfaf06e6944f",
    "DEBOUNCE_OPEN": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: started stats disk flush debounce window: duration=1s",
    "PREP": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: preparing stats upload from disk: only_if_file_is_old=false, reason=UPLOAD_REASON_PERIODIC",
    "ENQ": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: prepared UPLOAD_REASON_PERIODIC stats upload: uuid=72039116-da56-4b4e-90b2-635542f45c1d, snapshots=1, metrics=18",
    "DISPATCH": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: dispatched periodic stats upload for 1 source files",
    "DEBOUNCE_SHUT": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: stats disk flush debounce window closed without a trailing flush",
    "RES": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: periodic stats upload completed: uuid=72039116-da56-4b4e-90b2-635542f45c1d, success=true, source_files=1",
    "OS_FREEZE": "08-17 12:00:00.000  1000  1001 I am_freeze: [28588,com.google.android.gms]",
    "PLATFORM_FLUSH": "08-17 12:00:00.000  1000  1001 D bd_logger::logger: state flushing initiated",
    "DEBOUNCE_COALESCE": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: coalescing stats disk flush into active debounce window",
    "DEBOUNCE_TRAILING": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: running debounced trailing stats disk flush: periodic_upload_pending=false",
    "LOGBATCH": "08-17 12:00:00.000  1000  1001 D bd_logger::consumer: flushing 18 logs from trigger artifact",
    "STREAM_UP": "08-17 12:00:00.000  1000  1001 D bd_api::api: received handshake",
    "TICK": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: processing flush to disk tick",
    "FLUSHED": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: stats flushed",
    "STREAM_DOWN": "08-17 12:00:00.000  1000  1001 D bd_api::api: stream closed due to 'java.net.SocketException: Software caused connection abort'",
    "DEBO": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: skipping periodic upload, minimum interval not elapsed",
    "ACK": "08-17 12:00:00.000  1000  1001 D bd_api::api: received ack for stats upload \"e1ece423-c120-4db6-9dc2-838d920791a2\", error: \"\"",
    "BLOCK_OK": "08-17 12:00:00.000  1000  1001 D bd_logger::async_log_buffer: flush state: completion received",
    "BLOCK_ERR": "08-17 12:00:00.000  1000  1001 D bd_logger::async_log_buffer: flush state: received an error when waiting for completion: timed out",
    "DROPPED": "08-17 12:00:00.000  1000  1001 D bd_client_stats::stats: flush already in progress, skipping",
    "BUF": "08-17 12:00:00.000  1000  1001 D bd_buffer::ring_buffer: buffer_id=default_buffer_id signaled to flush"
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--capture", help="also assert an end-to-end parse of a real capture")
    args = ap.parse_args()

    fails, notes = [], []

    # 1. Every pattern needs a sample. A new pattern with no sample is untested by construction,
    #    which is exactly how the coalesce pattern survived for months while matching nothing.
    kinds = [p[0] for p in parse_logs.PATTERNS]
    for k in kinds:
        if k not in SAMPLES:
            fails.append(f"{k}: no sample — pattern is untested; add a real line from a capture")

    # 2. Each sample must match, and match as its own kind. First-match-wins means a broadened
    #    pattern can quietly swallow another kind's lines, which no per-pattern check would catch.
    for kind, line in SAMPLES.items():
        m = parse_logs.LINE.match(line)
        if not m:
            fails.append(f"{kind}: LINE regex did not parse the sample at all")
            continue
        tag, msg = m["tag"].strip(), m["msg"].strip()
        hit = next((k for k, tre, mre, _ in parse_logs.COMPILED
                    if tre.fullmatch(tag) and mre.search(msg)), None)
        if hit is None:
            fails.append(f"{kind}: NO pattern matches its own sample — renamed message or broken regex")
        elif hit != kind:
            fails.append(f"{kind}: sample matched as {hit} instead — pattern order or overlap bug")
        elif kind in SYNTHETIC:
            notes.append(f"{kind} (synthetic sample — wording not witnessed on a device)")

    # 3. End-to-end: a real capture should yield a plausible spread, not one lucky family.
    if args.capture:
        evs = parse_logs.parse(args.capture)
        got = {e["kind"] for e in evs}
        if len(evs) < 10:
            fails.append(f"capture: only {len(evs)} events recognised — pattern rot or wrong file")
        for need in ("PROC", "SNAP"):
            if need not in got:
                fails.append(f"capture: no {need} events — a capture without these is not usable")
        print(f"capture: {len(evs)} events, {len(got)} distinct kinds")

    for n in notes:
        print(f"  note: {n}")
    if fails:
        print(f"\nFAILED ({len(fails)}):")
        for f in fails:
            print(f"  - {f}")
        sys.exit(1)
    print(f"\nOK — {len(kinds)} patterns, all match a sample "
          f"({len(SYNTHETIC)} synthetic)")


if __name__ == "__main__":
    main()
