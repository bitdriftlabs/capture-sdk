#!/usr/bin/env python3
"""Collect opt-in sample trigger evidence; never infer a parent from callback/observer identity."""
import argparse
import hashlib
import json
import pathlib
import re
import time

from common import PKG, TP, adb, tp

REMOTE = f"/sdcard/Android/data/{PKG}/files/trigger-profiling"
NAME = re.compile(r"[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\.perfetto-trace")
SHA256 = re.compile(r"[0-9a-f]{64}")
MAX_BYTES = 32 * 1024 * 1024
CLOCK_TOLERANCE_NS = 1_000_000_000
QUERIES = {
    "bounds": "SELECT * FROM trace_bounds",
    "processes": "SELECT pid,name,start_ts,end_ts,uid,cmdline FROM process WHERE pid>0 LIMIT 129",
    "packages": "SELECT * FROM package_list LIMIT 129",
    "clocks": """SELECT clock_id,clock_name,COUNT(*) snapshots,MIN(ts) first_ts,MAX(ts) last_ts,
        MIN(clock_value-ts) min_offset_ns,MAX(clock_value-ts) max_offset_ns
        FROM clock_snapshot GROUP BY clock_id,clock_name ORDER BY clock_id""",
    "metadata": """SELECT name,str_value,int_value FROM metadata
        WHERE name IN ('trace_uuid','trace_time_clock_id','android_build_fingerprint',
        'android_sdk_version','tracing_service_version','tracing_started_ns','tracing_disabled_ns')""",
    "anrs": "INCLUDE PERFETTO MODULE android.anrs; SELECT * FROM android_anrs LIMIT 129",
    "heap_graph": """SELECT p.pid,COUNT(*) objects,SUM(o.self_size) self_bytes,
        MIN(o.graph_sample_ts) first_ts,MAX(o.graph_sample_ts) last_ts
        FROM heap_graph_object o JOIN process p USING(upid) GROUP BY p.pid""",
    "pid_observations": """WITH activity AS (
        SELECT t.upid, MIN(s.ts) first_seen, MAX(s.ts) last_seen
        FROM sched_slice s JOIN thread t USING(utid) GROUP BY t.upid
        UNION ALL
        SELECT upid, MIN(graph_sample_ts), MAX(graph_sample_ts)
        FROM heap_graph_object GROUP BY upid)
        SELECT p.upid,p.pid,p.name,p.start_ts,p.end_ts,
        MIN(a.first_seen) first_seen,MAX(a.last_seen) last_seen
        FROM process p LEFT JOIN activity a USING(upid) WHERE p.pid>0
        GROUP BY p.upid LIMIT 129""",
}

def process_context(profile, observer, events):
    """Experimental process membership, not exact-trigger attribution or a production join."""
    data = profile["trace_processor"]
    if profile["candidate_read_overflow"]:
        return {"status": "incomplete_candidate_read", "matches": []}
    if not observer.get("installation_id"):
        return {"status": "missing_installation_scope", "matches": []}
    clocks = {row["clock_name"]: row for row in data["clocks"]}
    if not all(name in clocks for name in ("REALTIME", "BOOTTIME")):
        return {"status": "missing_clock_mapping", "matches": []}
    for name in ("REALTIME", "BOOTTIME"):
        row = clocks[name]
        if int(row["max_offset_ns"]) - int(row["min_offset_ns"]) > CLOCK_TOLERANCE_NS:
            return {"status": "unstable_clock_mapping", "matches": []}
    boot_offset = int(clocks["BOOTTIME"]["min_offset_ns"])
    boot_epoch = int(clocks["REALTIME"]["min_offset_ns"]) - boot_offset
    bounds = data["bounds"]
    if len(bounds) != 1:
        return {"status": "missing_trace_bounds", "matches": []}
    trace_start = int(bounds[0]["start_ts"]) + boot_offset
    trace_end = int(bounds[0]["end_ts"]) + boot_offset
    matches = []
    seen = set()
    for event in events:
        if event["event"] != "launch":
            continue
        run = event["observer"]
        if run.get("installation_id") != observer["installation_id"] or run["run_id"] in seen:
            continue
        seen.add(run["run_id"])
        if run["boot_count"] < 0:
            continue
        run_epoch = run["wall_ms"] * 1_000_000 - run["elapsed_ns"]
        if abs(run_epoch - boot_epoch) > CLOCK_TOLERANCE_NS:
            continue
        run_start = run["start_elapsed_ms"] * 1_000_000
        if not 0 <= run_start <= run["elapsed_ns"]:
            raise ValueError(f"Invalid startup snapshot for run {run['run_id']}")
        run_end = trace_end
        for other in events:
            if other["event"] != "exit_info":
                continue
            exit_observer = other["observer"]
            detail = other["details"]
            if (exit_observer.get("installation_id") == run["installation_id"]
                    and detail["source_pid"] == run["pid"]
                    and detail["timestamp_ms"] * 1_000_000 >= run_start + run_epoch):
                run_end = min(run_end, detail["timestamp_ms"] * 1_000_000 - run_epoch)
        pid_ranges = []
        for row in data["pid_observations"]:
            if int(row["pid"]) != run["pid"]:
                continue
            if row["first_seen"] is None or row["last_seen"] is None:
                continue
            first = max(trace_start, int(row["first_seen"]) + boot_offset)
            last = min(trace_end, int(row["last_seen"]) + boot_offset)
            if row["start_ts"] is not None:
                first = max(first, int(row["start_ts"]) + boot_offset)
            if row["end_ts"] is not None:
                last = min(last, int(row["end_ts"]) + boot_offset)
            if max(first, run_start) <= min(last, run_end):
                pid_ranges.append({"first_ns": first, "last_ns": last, "upid": row["upid"]})
        if pid_ranges:
            matches.append({
                "run_id": run["run_id"], "pid": run["pid"], "boot_count": run["boot_count"],
                "installation_id": run["installation_id"], "pid_ranges": pid_ranges,
                "source_version_code": run["version_code"], "lookup_version_code": observer["version_code"],
                "observer_is_source": run["run_id"] == observer["run_id"],
                "boot_epoch_difference_ns": run_epoch - boot_epoch,
            })
    return {"status": "matched" if matches else "unmatched", "matches": matches,
            "semantics": "process_context_not_trigger_causality", "clock_tolerance_ns": CLOCK_TOLERANCE_NS}


def deliveries(events):
    saved = []
    files = {}
    for event in events:
        if event["event"] != "profile_saved":
            continue
        detail = event["details"]
        name, digest, size = detail["file"], detail["sha256"], detail["raw_bytes"]
        if not NAME.fullmatch(name) or not SHA256.fullmatch(digest):
            raise ValueError("Invalid exported profile name or SHA-256")
        if type(size) is not int or not 0 < size <= MAX_BYTES:
            raise ValueError(f"Profile exceeds the collector's {MAX_BYTES}-byte envelope")
        identity = (digest, size, detail["trigger_type"])
        if name in files and files[name] != identity:
            raise ValueError("Conflicting metadata for the same exported file")
        files[name] = identity
        saved.append(event)
    if len(files) > 16:
        raise ValueError("More than 16 exported profiles; isolate the experiment")
    return saved, files


def verify(path, expected_digest, expected_size):
    if path.stat().st_size != expected_size:
        raise ValueError(f"Size mismatch: {path.name}")
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(64 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != expected_digest:
        raise ValueError(f"SHA-256 mismatch: {path.name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--analyze", action="store_true", help="Run the existing local trace_processor")
    args = parser.parse_args()
    if args.analyze and not TP:
        parser.error("trace_processor is missing; set TRACE_PROCESSOR to the official local tool")
    args.out.mkdir(parents=True, exist_ok=True)
    events_path = args.out / "events.jsonl"
    adb("-s", args.serial, "pull", f"{REMOTE}/events.jsonl", str(events_path))
    if events_path.stat().st_size > 4 * 1024 * 1024:
        raise ValueError("More than 4 MiB of event history; isolate the experiment")
    events = [json.loads(line) for line in events_path.read_text().splitlines()]
    for event in events:
        event["observer"]["installation_id"] = event.get("installation_id")
    saved, files = deliveries(events)
    profiles = {}
    for name, (digest, size, kind) in files.items():
        path = args.out / name
        if not path.exists():
            partial = args.out / f"{name}.part"
            adb("-s", args.serial, "pull", f"{REMOTE}/{name}", str(partial))
            verify(partial, digest, size)
            partial.replace(path)
        else:
            verify(path, digest, size)
        profile = {"sha256": digest, "raw_bytes": size, "trigger_type": kind}
        if args.analyze:
            started = time.monotonic()
            profile["trace_processor"] = {key: tp(path, sql) for key, sql in QUERIES.items()}
            profile["inspection_elapsed_seconds"] = time.monotonic() - started
            profile["candidate_read_overflow"] = any(
                len(profile["trace_processor"][key]) > 128 for key in ("processes", "packages", "anrs", "pid_observations")
            )
            delivery = next(row for row in saved if row["details"]["file"] == name)
            profile["original_filename"] = delivery["details"].get("original_filename")
            profile["process_context"] = process_context(profile, delivery["observer"], events)
        profiles[name] = profile
    result = {
        "profiles": profiles,
        "deliveries": saved,
        "callback_errors": [
            row for row in events if row["event"] == "callback" and row["details"]["error_code"] != 0
        ],
        "association": "not_established",
        "warning": "Process-context experiment only; observer identity is not source identity and no canonical report join is implemented.",
    }
    (args.out / "inventory.json").write_text(json.dumps(result, indent=2) + "\n")
    print(f"Verified {len(files)} files and preserved {len(saved)} deliveries; no report association inferred.")


if __name__ == "__main__":
    main()
