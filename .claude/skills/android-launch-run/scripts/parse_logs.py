#!/usr/bin/env python3
"""Turn a logcat capture into a device-clock timeline plus a verdict summary.

  python3 parse_logs.py <capture.txt> [--ref "process ON_STOP"] [--uid 10304] [--json]

Everything is measured relative to a reference event (default `process ON_STOP`, the point the
SDK's backgrounding work hangs off), so offsets read as "+18ms after backgrounding" rather than as
wall-clock times nobody can hold in their head.

Emits a run-validity warning when the reference event precedes the action marker: that means the
app was already backgrounded when the action landed (usually a locked device), and the run measured
nothing.
"""
from __future__ import annotations

import argparse
import json
import re
import sys

LINE = re.compile(
    r"^(?P<ts>\d\d-\d\d \d\d:\d\d:\d\d\.\d\d\d)\s+(?P<pid>\d+)\s+\d+\s+[VDIWEF]\s+(?P<tag>.*?):\s(?P<msg>.*)$"
)

# (kind, tag regex, message regex, human label). Order matters only for first-match-wins.
PATTERNS: list[tuple[str, str, str, str]] = [
    # harness markers
    ("MARK", r"ANDROID-RUN", r"^ACTION (?P<what>.+)$", ">>> {what}"),
    # app-side lifecycle
    ("PROC", r"bitdrift-lifecycle", r"^process (?P<ev>\S+)$", "process {ev}"),
    ("FOCUS", r"bitdrift-lifecycle", r"^window (?P<act>\S+) focus=(?P<f>\S+)$",
     "window focus={f} ({act})"),
    # OS lifecycle (events buffer); tag differs by Android version
    ("OS_STOP", r"(wm|am)_on_stop_called", r".*", "OS: activity onStop"),
    ("OS_PAUSE", r"(wm|am)_on_paused_called", r".*", "OS: activity onPause"),
    ("OS_FOCUS", r"input_focus", r"^(?P<what>Focus leaving|ViewRootImpl focus=false).*",
     "OS: {what}"),
    ("OS_FREEZE", r"am_freeze", r".*", "OS: am_freeze"),
    # flush attribution
    ("PLATFORM_FLUSH", r"bd_logger::logger", r"^state flushing initiated$",
     "state flushing initiated  [PLATFORM asked]"),
    ("BLOCK_OK", r"bd_logger::async_log_buffer", r"^flush state: completion received$",
     "blocking flush completed"),
    ("BLOCK_ERR", r"bd_logger::async_log_buffer",
     r"^flush state: received an error when waiting for completion",
     "blocking flush TIMED OUT (500ms JNI cap)"),
    # stats — match the whole bd_client_stats* prefix, not just ::stats
    ("FORCED", r"bd_client_stats.*", r"^received a signal to flush stats to disk$",
     "forced flush requested"),
    ("TICK", r"bd_client_stats.*", r"^processing flush to disk tick$", "flush_to_disk() entered"),
    ("MERGE", r"bd_client_stats.*", r"^updating aggregated snapshot file with (?P<n>\d+) metrics",
     "merge snapshot ({n} metrics)"),
    ("SNAP", r"bd_client_stats.*", r"^writing snapshot: (?P<path>\S+)$",
     "WROTE SNAPSHOT TO DISK  {path}"),
    ("FLUSHED", r"bd_client_stats.*", r"^stats flushed$", "stats flushed (forced path complete)"),
    ("ENQ", r"bd_client_stats.*",
     r"^sending pending flush upload: (?P<uuid>\S+) with (?P<n>\d+) metrics$",
     "upload enqueued {uuid} ({n} metrics)"),
    ("DEBO", r"bd_client_stats.*", r"^skipping flush upload, minimum interval not elapsed$",
     "upload DEBOUNCED (30s window)"),
    ("DROPPED", r"bd_client_stats.*", r"^flush already in progress, skipping$",
     "flush DROPPED (already in progress)"),
    ("RES", r"bd_client_stats.*",
     r'^stat flush upload attempt complete: UploadResponse \{ success: (?P<ok>\w+), uuid: "(?P<uuid>[^"]*)"',
     "upload result success={ok} {uuid}"),
    # transport
    ("STREAM_UP", r"bd_api.*", r"^received handshake$", "api stream up"),
    ("STREAM_DOWN", r"bd_api.*", r"^stream closed due to '(?P<why>.*)'$", "api stream CLOSED: {why}"),
    # ring buffers / log uploads
    ("BUF", r"bd_buffer.*", r"^buffer_id=(?P<id>\S+) signaled to flush$", "ring buffer flush {id}"),
    ("LOGBATCH", r"bd_logger::consumer", r"^flushing (?P<n>\d+) logs", "log batch: {n} logs"),
]
COMPILED = [(k, re.compile(t), re.compile(m), lbl) for k, t, m, lbl in PATTERNS]


def ts_ms(ts: str) -> int:
    date, clock = ts.split(" ")
    mo, dd = (int(x) for x in date.split("-"))
    hh, mm, rest = clock.split(":")
    ss, ms = rest.split(".")
    return ((((mo - 1) * 31 + dd) * 24 + int(hh)) * 3600 + int(mm) * 60 + int(ss)) * 1000 + int(ms)


def parse(path: str, pkg: str = "io.bitdrift.gradletestapp") -> list[dict]:
    """Parse a capture, keeping only events attributable to `pkg`.

    Two sources of cross-talk to filter out. The `events` buffer is system-wide, so `am_freeze` and
    friends report every app on the device. And the main buffer can carry `bd_*` lines from another
    bitdrift app installed alongside this one — the pid tells them apart.
    """
    out = []
    with open(path, errors="replace") as fh:
        for raw in fh:
            m = LINE.match(raw.rstrip("\n"))
            if not m:
                continue
            tag, msg = m["tag"].strip(), m["msg"].strip()
            for kind, tre, mre, label in COMPILED:
                if not tre.fullmatch(tag):
                    continue
                mm = mre.search(msg)
                if not mm:
                    continue
                # events-buffer tags are system-wide: require our package in the payload.
                if kind.startswith("OS_") and pkg not in msg:
                    break
                groups = {k: v for k, v in mm.groupdict().items() if v}
                out.append({"kind": kind, "t": ts_ms(m["ts"]), "ts": m["ts"],
                            "pid": m["pid"],
                            "label": label.format(**groups) if groups else label, **groups})
                break

    # Our pid is whichever process emitted the app-side lifecycle logs. Use it to drop bd_* lines
    # belonging to a different bitdrift app on the same device.
    ours = {e["pid"] for e in out if e["kind"] in ("PROC", "FOCUS")}
    if ours:
        out = [e for e in out
               if e["kind"].startswith("OS_") or e["kind"] == "MARK" or e["pid"] in ours]
    return out


def fmt(delta: int) -> str:
    a = abs(delta)
    s = "+" if delta >= 0 else "-"
    return f"{s}{a/1000:.3f}s" if a >= 1000 else f"{s}{a}ms"


def summarize(evs: list[dict], ref_label: str) -> dict:
    ref = next((e for e in evs if e["kind"] == "PROC" and ref_label.endswith(e.get("ev", "\0"))), None)
    if ref is None:
        ref = next((e for e in evs if ref_label in e["label"]), None)
    ref_t = ref["t"] if ref else (evs[0]["t"] if evs else 0)

    after = [e for e in evs if e["t"] >= ref_t - 50]
    before = [e for e in evs if e["t"] < ref_t - 50]
    k = lambda src, kind: [e for e in src if e["kind"] == kind]

    # Only an enqueue belonging to this flush counts — a later periodic upload is unrelated.
    forced_after = k(after, "FORCED")
    fref = forced_after[0]["t"] if forced_after else ref_t
    own_enq = [e for e in k(after, "ENQ") if fref <= e["t"] <= fref + 3000]
    own_debo = [e for e in k(after, "DEBO") if fref <= e["t"] <= fref + 3000]

    res = None
    if own_enq:
        res = next((e for e in k(evs, "RES") if e.get("uuid") == own_enq[0].get("uuid")), None)

    action = next((e for e in evs if e["kind"] == "MARK"
                   and not e["label"].endswith(("launch", "observe-end"))
                   and "wait" not in e["label"]), None)
    invalid = bool(ref and action and ref["t"] < action["t"] - 50)

    return {
        "ref_t": ref_t,
        "ref_found": ref is not None,
        "invalid_run": invalid,
        "fg": {"snapshots": len(k(before, "SNAP")), "uploads_enqueued": len(k(before, "ENQ")),
               "uploads_ok": len([e for e in k(before, "RES") if e.get("ok") == "true"])},
        "bg": {
            "platform_flush": len(k(after, "PLATFORM_FLUSH")),
            "forced": len(forced_after),
            "timed": len([e for e in k(evs, "TICK")
                          if not any(0 <= e["t"] - f["t"] <= 150 for f in k(evs, "FORCED"))]),
            "dropped": len(k(evs, "DROPPED")),
            "snapshot_written": len(k(after, "SNAP")),
            "stats_flushed": len(k(after, "FLUSHED")),
            "upload": "ENQ" if own_enq else ("DEBO" if own_debo else "NONE"),
            "upload_result": ("OK" if res and res.get("ok") == "true"
                              else "FAIL" if res else "NONE"),
            "ack_ms": (res["t"] - own_enq[0]["t"]) if (res and own_enq) else None,
        },
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("capture")
    ap.add_argument("--ref", default="process ON_STOP")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    evs = parse(args.capture)
    if not evs:
        sys.exit("no recognised events — check the capture and that RUST_LOG was set before launch")
    s = summarize(evs, args.ref)

    if args.json:
        print(json.dumps({"summary": s, "events": evs}, indent=2))
        return

    print(f"reference: {args.ref}" + ("" if s["ref_found"] else "  (NOT FOUND — offsets are from "
                                                               "the first event)"))
    if s["invalid_run"]:
        print("\n  *** INVALID RUN: the reference event precedes the action marker. The app was "
              "\n      already backgrounded when the action landed — usually a locked device. "
              "\n      Discard and re-run with the device unlocked. ***\n")
    print()
    for e in evs:
        d = e["t"] - s["ref_t"]
        strong = e["kind"] in ("MARK", "SNAP", "PLATFORM_FLUSH", "BLOCK_ERR", "DROPPED")
        print(f"  {fmt(d):>9}  {'#' if strong else '|'} {e['label']}")

    fg, bg = s["fg"], s["bg"]
    print(f"\nFOREGROUND (before reference): {fg['snapshots']} snapshot write(s) · "
          f"{fg['uploads_enqueued']} upload(s) enqueued, {fg['uploads_ok']} acked")
    print(f"BACKGROUND (after reference):  snapshot written: "
          f"{'YES' if bg['snapshot_written'] else 'NO'} · "
          f"platform flush: {bg['platform_flush']} · forced: {bg['forced']} · "
          f"timed: {bg['timed']} · dropped: {bg['dropped']}")
    print(f"  upload {bg['upload']}/{bg['upload_result']}"
          + (f" · ack {bg['ack_ms']}ms" if bg["ack_ms"] is not None else ""))
    if bg["upload"] == "DEBO":
        print("  NOTE: debounced by the 30s window — this run did not test upload delivery. "
              "Idle >30s in the foreground before backgrounding.")


if __name__ == "__main__":
    main()
