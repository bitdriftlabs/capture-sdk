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
    r"^(?P<ts>\d\d-\d\d \d\d:\d\d:\d\d\.\d{3,6})\s+(?P<pid>\d+)\s+\d+\s+[VDIWEF]\s+(?P<tag>.*?):\s(?P<msg>.*)$"
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
    # Stats. Match the whole bd_client_stats* prefix, not just ::stats.
    #
    # The message text changed with the shared-core bump to c3ba1cba, so both wordings are matched:
    # older revs are still in the wild, and a pattern that silently stops matching reports "no
    # upload happened" rather than "I can't see it" — the worst possible failure mode here.
    ("FORCED", r"bd_client_stats.*",
     r"^(received a signal to flush stats to disk|flushing collected stats to disk)$",
     "forced flush requested"),
    ("TICK", r"bd_client_stats.*", r"^processing flush to disk tick$",
     "flush to disk tick entered"),
    ("MERGE", r"bd_client_stats.*", r"^updating aggregated snapshot file with (?P<n>\d+) metrics",
     "merge snapshot ({n} metrics)"),
    ("SNAP", r"bd_client_stats.*", r"^writing snapshot: (?P<path>\S+)$",
     "WROTE SNAPSHOT TO DISK  {path}"),
    ("FLUSHED", r"bd_client_stats.*", r"^stats flushed$",
     "stats flushed (forced path complete)"),
    ("PREP", r"bd_client_stats.*",
     r"^preparing stats upload from disk: only_if_file_is_old=(?P<old>\w+), reason=(?P<reason>\S+)",
     "preparing upload ({reason})"),
    ("ENQ", r"bd_client_stats.*",
     r"^(?:sending pending flush upload: (?P<uuid>\S+) with (?P<n>\d+) metrics"
     r"|prepared (?P<reason>\S+) stats upload: uuid=(?P<uuid2>[0-9a-f-]+), snapshots=(?P<snaps>\d+), metrics=(?P<n2>\d+))$",
     "upload enqueued"),
    ("DISPATCH", r"bd_client_stats.*",
     r"^dispatched (?P<kind>.+?) stats upload for (?P<n>\d+) source files$",
     "dispatched {kind} upload ({n} files)"),
    # `skipping <kind> upload, …` — kind is flush/periodic/absent depending on rev and call site.
    # Pinning the word (it used to be a literal "flush") made a live gate invisible: rev 42637e1f
    # emits "skipping periodic upload", which fell through and reported as upload NONE.
    # Surface which path was refused: a `periodic` suppression is the gate working as designed (a 5s
    # cadence against a 30s floor refuses constantly), whereas a `flush` suppression means the
    # backgrounding upload never went out and the run measured the gate instead of the question.
    ("DEBO", r"bd_client_stats.*",
     r"^(?:skipping (?:(?P<kind0>\S+) )?upload, minimum interval not elapsed"
     r"|skipping (?P<kind>\S+) stats upload: minimum upload interval has not elapsed)$",
     "upload DEBOUNCED ({kind} path, minimum interval)"),
    ("DEBOUNCE_OPEN", r"bd_client_stats.*",
     r"^started stats disk flush debounce window: duration=(?P<d>\S+)$",
     "disk-flush debounce window OPENED ({d})"),
    ("DEBOUNCE_SHUT", r"bd_client_stats.*",
     r"^stats disk flush debounce window closed without a trailing flush$",
     "disk-flush debounce window closed, nothing coalesced"),
    ("DEBOUNCE_COALESCE", r"bd_client_stats.*",
     r"^stats disk flush debounce window closed(?! without).*$",
     "disk-flush debounce window closed WITH a coalesced flush"),
    ("DROPPED", r"bd_client_stats.*", r"^flush already in progress, skipping$",
     "flush DROPPED (already in progress)"),
    # The server ack, logged one step before the UploadResponse summary. It is the most direct
    # evidence an upload actually landed, so surface it rather than inferring delivery from RES.
    # Note the tag is bd_api, not bd_client_stats — the ack is observed at the transport layer,
    # which is why a stats-only RUST_LOG filter hides it. The id is a uuid on some paths and a
    # 64-char content hash on others, so match neither shape.
    ("ACK", r"bd_api.*",
     r'^received ack for stats upload "(?P<uuid>[^"]*)", error: "(?P<err>[^"]*)"$',
     "server ACK for upload"),
    ("RES", r"bd_client_stats.*",
     r'^(?:stat flush upload attempt complete: UploadResponse \{ success: (?P<ok>\w+), uuid: "(?P<uuid>[^"]*)"'
     r'|(?P<kind2>.+?) stats upload completed: uuid=(?P<uuid2>[0-9a-f-]+), success=(?P<ok2>\w+), source_files=(?P<files>\d+))',
     "upload result"),
    # transport
    ("STREAM_UP", r"bd_api.*", r"^received handshake$", "api stream up"),
    ("STREAM_DOWN", r"bd_api.*", r"^stream closed due to '(?P<why>.*)'$", "api stream CLOSED: {why}"),
    # ring buffers / log uploads
    ("BUF", r"bd_buffer.*", r"^buffer_id=(?P<id>\S+) signaled to flush$", "ring buffer flush {id}"),
    ("LOGBATCH", r"bd_logger::consumer", r"^flushing (?P<n>\d+) logs", "log batch: {n} logs"),
]
COMPILED = [(k, re.compile(t), re.compile(m), lbl) for k, t, m, lbl in PATTERNS]


def ts_ms(ts: str) -> int:
    """Device-clock timestamp to milliseconds.

    Handles both `-v threadtime` (`.345`) and `-v usec` (`.000124`) fractions — padding then
    truncating to three digits gives ms in either case.
    """
    date, clock = ts.split(" ")
    mo, dd = (int(x) for x in date.split("-"))
    hh, mm, rest = clock.split(":")
    ss, frac = rest.split(".")
    ms = int(frac.ljust(6, "0")[:3])
    return ((((mo - 1) * 31 + dd) * 24 + int(hh)) * 3600 + int(mm) * 60 + int(ss)) * 1000 + ms


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
                # Old/new wordings use different group names for the same field.
                if "uuid2" in groups:
                    groups["uuid"] = groups.pop("uuid2")
                if "ok2" in groups:
                    groups["ok"] = groups.pop("ok2")
                if "n2" in groups:
                    groups["n"] = groups.pop("n2")
                if "kind0" in groups:
                    groups["kind"] = groups.pop("kind0")
                # Some revs log a bare "skipping upload, …" with no kind. The label still needs the
                # placeholder filled, and an unnamed path is worth flagging rather than papering over.
                if kind == "DEBO":
                    groups.setdefault("kind", "unspecified")
                # Reserved fields must win over regex capture groups. A pattern with a
                # (?P<kind>…) group would otherwise clobber the event type via the splat and
                # silently vanish from every downstream count.
                ev = dict(groups)
                ev.update({"kind": kind, "t": ts_ms(m["ts"]), "ts": m["ts"], "pid": m["pid"],
                           "label": label.format(**groups) if groups else label})
                out.append(ev)
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



def _debounce_report(evs: list[dict]) -> dict:
    """Validate the c3ba1cba disk-flush debounce window.

    The window opens right after each disk write and lasts `duration`. Its job is to coalesce a
    second flush arriving inside that span into one write. Rather than trying to force a
    coalesce — which needs two flushes landing <1s apart and is hard to schedule — check the
    invariant it implies: consecutive `writing snapshot` events should never be closer together
    than the window. A violation means writes are slipping through un-debounced.
    """
    opens = [e for e in evs if e["kind"] == "DEBOUNCE_OPEN"]
    shuts = [e for e in evs if e["kind"] == "DEBOUNCE_SHUT"]
    coal = [e for e in evs if e["kind"] == "DEBOUNCE_COALESCE"]
    writes = sorted(e["t"] for e in evs if e["kind"] == "SNAP")
    gaps = [b - a for a, b in zip(writes, writes[1:])]

    dur_ms = None
    if opens:
        raw = opens[0].get("d", "")
        m = re.match(r"([\d.]+)(ms|s)$", raw)
        if m:
            dur_ms = float(m.group(1)) * (1 if m.group(2) == "ms" else 1000)

    min_gap = min(gaps) if gaps else None
    held = None
    if dur_ms is not None and min_gap is not None:
        held = min_gap >= dur_ms
    return {"present": bool(opens), "window_ms": dur_ms, "opened": len(opens),
            "closed_empty": len(shuts), "closed_coalesced": len(coal),
            "writes": len(writes), "min_write_gap_ms": min_gap, "invariant_held": held}


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

    no_ref = ref is None
    return {
        "ref_t": ref_t,
        "ref_found": not no_ref,
        "no_reference": no_ref,
        "invalid_run": invalid,
        "debounce": _debounce_report(evs),
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

    print(f"reference: {args.ref}")
    if s["no_reference"]:
        print("\n  *** REFERENCE EVENT NEVER FIRED ***"
              "\n      The foreground/background split is meaningless, so no backgrounding verdict"
              "\n      is reported below — any counts would be foreground work mislabelled."
              "\n      If the reference is 'process ON_STOP', the usual cause is that the app never"
              "\n      actually backgrounded: the app-switcher/recents action leaves the activity"
              "\n      started, so no backgrounding work runs at all. That absence IS the result.\n")
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
    if s["no_reference"]:
        snaps = len([e for e in evs if e["kind"] == "SNAP"])
        enq = len([e for e in evs if e["kind"] == "ENQ"])
        ok = len([e for e in evs if e["kind"] == "RES" and e.get("ok") == "true"])
        print(f"WHOLE RUN (no phase split possible): {snaps} snapshot write(s) · "
              f"{enq} upload(s) enqueued, {ok} acked")
        print("BACKGROUNDING FLUSH: none ran — the reference event never fired.")
        return
    print(f"\nFOREGROUND (before reference): {fg['snapshots']} snapshot write(s) · "
          f"{fg['uploads_enqueued']} upload(s) enqueued, {fg['uploads_ok']} acked")
    print(f"BACKGROUND (after reference):  snapshot written: "
          f"{'YES' if bg['snapshot_written'] else 'NO'} · "
          f"platform flush: {bg['platform_flush']} · forced: {bg['forced']} · "
          f"timed: {bg['timed']} · dropped: {bg['dropped']}")
    print(f"  upload {bg['upload']}/{bg['upload_result']}"
          + (f" · ack {bg['ack_ms']}ms" if bg["ack_ms"] is not None else ""))
    db = s["debounce"]
    if db["present"]:
        verdict = ("HELD" if db["invariant_held"] else "VIOLATED") if db["invariant_held"] is not None else "n/a"
        print(f"DISK-FLUSH DEBOUNCE ({db['window_ms']:.0f}ms window): {db['opened']} opened · "
              f"{db['closed_empty']} closed empty · {db['closed_coalesced']} coalesced · "
              f"{db['writes']} write(s), closest {db['min_write_gap_ms']}ms apart "
              f"=> invariant {verdict}")
        if db["closed_coalesced"] == 0:
            print("  NOTE: no window ever coalesced a flush, so the coalescing path is untested "
                  "here — the invariant only shows writes never landed inside a window.")
    if bg["upload"] == "DEBO":
        print("  NOTE: debounced by the 30s window — this run did not test upload delivery. "
              "Idle >30s in the foreground before backgrounding.")


if __name__ == "__main__":
    main()
