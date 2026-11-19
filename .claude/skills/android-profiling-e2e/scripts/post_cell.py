#!/usr/bin/env python3
"""After run_cell.py: find the session and attachment, download raw / processed / default plus
/status, check byte integrity, unpack the TAR, and run the trace_processor summaries.

    post_cell.py --cell R-HPJ --type heap_profile_java --runs .tmp/.../runs [--workflow <id>]

--workflow is needed when there is no on-device copy (built-in commands): the session is then the
newest one the temp workflow captured.
"""
import argparse
import datetime
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tarfile
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import CELLS, bd, command_name, tp  # noqa: E402

PERFETTO_MIME = "application/vnd.bitdrift.perfetto-trace"
BUNDLE_MEMBERS = {"trace.perfetto", "symbols.pb", "deobfuscation.pb"}


def sha(p):
    return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()


def bd_json(*args):
    r = bd(*args, "-ojson")
    if r.returncode != 0:
        raise RuntimeError(f"bd {' '.join(args)} failed (exit {r.returncode})")
    try:
        return json.loads(r.stdout)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"bd {' '.join(args)} did not return JSON") from e


def epoch(ts):
    """'2026-11-05T17:24:24.975Z' (UTC) or '2026-11-05T12:20:01-0500' (run_cell's local time) -> seconds."""
    if not ts:
        return 0.0
    if ts.endswith("Z"):
        return datetime.datetime.strptime(ts[:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=datetime.timezone.utc).timestamp()
    return datetime.datetime.strptime(ts, "%Y-%m-%dT%H:%M:%S%z").timestamp()


def find_session(info, workflow, since, action_id=None, attempts=48):
    if not action_id:
        for records in ("traces", "invocations"):
            for record in reversed(info.get(records, [])):
                if record.get("session_id") not in (None, "null"):
                    return record["session_id"]
    if not workflow:
        sys.exit("no session id: pass --workflow <temp workflow id>")
    # Only a session the temp workflow captured during this run counts; keep polling for ingestion lag
    # instead of falling back to an older session.
    for _ in range(attempts):
        args = ["--action-id", action_id] if action_id else []
        sessions = bd_json("workflow", "captured-sessions", workflow, "--last", "2h", *args).get("captured_sessions", [])
        recent = [s for s in sessions if epoch(s.get("last_seen")) >= since]
        if action_id and (len(recent) > 1 or any(int(s["count"]) != 1 for s in recent)):
            raise RuntimeError(f"workflow {workflow} produced more than one capture for action {action_id}")
        if recent:
            return max(recent, key=lambda s: epoch(s.get("last_seen")))["session_id"]
        time.sleep(5)
    sys.exit(f"workflow {workflow} captured no session after the run started")


def command_key(log):
    details = ((log.get("fields") or {}).get("fields", {}).get("_command_details") or {}).get("string_data") or "{}"
    try:
        return json.loads(details).get("key")
    except json.JSONDecodeError:
        return None


def find_completion(d, sid, name, since, action_id=None, attempts=48):
    """The newest attachment-bearing completion of exactly this command, logged after the run started."""
    for _ in range(attempts):
        logs = bd_json("timeline", "logs", sid).get("logs", [])
        (d / "timeline.json").write_text(json.dumps({"logs": logs}))
        done = [l for l in logs if l.get("message") == "Command completed"
                and (action_id in l.get("listener_ids", []) if action_id else l.get("command_attachment"))
                and command_key(l) == name and epoch(l.get("timestamp")) >= since]
        if action_id and len(done) > 1:
            raise RuntimeError(f"session {sid} has more than one {name} completion for action {action_id}")
        if done:
            return max(done, key=lambda l: epoch(l.get("timestamp")))
        time.sleep(5)
    sys.exit(f"no completed {name} with an attachment in session {sid} since the run started")


def heap_profile_evidence(raw, processed):
    sql = """select
      (select count(*) from heap_profile_allocation where count > 0 and callsite_id is not null) allocation_rows,
      (select coalesce(sum(count), 0) from heap_profile_allocation where count > 0 and callsite_id is not null) samples,
      (select count(*) from perf_sample) cpu_samples"""
    counts = {label: tp(path, sql)[0] for label, path in (("raw", raw), ("processed", processed))}
    for label, values in counts.items():
        if int(values["samples"]) <= 0 or int(values["cpu_samples"]) != 0:
            raise RuntimeError(f"{label} is not a sampled heap allocation profile: {values}")
    if counts["raw"] != counts["processed"]:
        raise RuntimeError(f"processing changed allocation sample counts: {counts}")
    return counts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cell", required=True)
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--runs", required=True)
    ap.add_argument("--workflow")
    ap.add_argument("--action-id", help="the actual flush action ID from workflow describe")
    ap.add_argument("--local", action="store_true", help="download from local Envoy using BD_API_KEY")
    ap.add_argument("--marker", help="require this workflow_e2e_run value on the completion")
    a = ap.parse_args()
    strict = a.type == "heap_profile_trace"
    if strict and not (a.workflow and a.action_id):
        ap.error("heap_profile_trace requires --workflow and --action-id")
    if a.local and (not os.environ.get("BD_CLI") or not os.environ.get("BD_API_KEY")):
        ap.error("--local requires an explicit local BD_CLI command and BD_API_KEY")
    d = pathlib.Path(a.runs) / a.cell
    info = json.loads((d / "cell.json").read_text())
    since = epoch(info["started"]) - 60  # small allowance for clock skew between host and backend
    sid = find_session(info, a.workflow, since, a.action_id)
    done = find_completion(d, sid, command_name(a.type), since, a.action_id)
    att = done.get("command_attachment") or {}
    fields = (done.get("fields") or {}).get("fields", {})
    pick = lambda k: (fields.get(k) or {}).get("string_data")
    if strict and (
        pick("_result") != "success" or "_command_id" in fields
        or (a.marker and pick("workflow_e2e_run") != a.marker)
        or att.get("source") != "WORKFLOW" or att.get("content_type") != PERFETTO_MIME
        or int(att.get("size_bytes", 0)) <= 0 or not att.get("artifact_id")
    ):
        sys.exit(f"expected a successful typed workflow Perfetto attachment; see {d / 'timeline.json'}")
    summary = {"cell": a.cell, "type": a.type, "session_id": sid, "artifact_id": att["artifact_id"],
               "attachment": att, "completed_at": done.get("timestamp"),
               "app_version_code": pick("_app_version_code"), "command_result": pick("_result"),
               "command_details": pick("_command_details")}

    args = ["--local"] if a.local else []
    subprocess.run([sys.executable, str(HERE / "fetch_backend.py"), att["artifact_id"], "--out", str(d), *args], check=True)
    backend = json.loads((d / "backend.json").read_text())
    raw, tarp, dflt = d / "backend_raw.perfetto-trace", d / "backend_processed.tar", d / "backend_default.tar"

    integ = {}
    dev = next((pathlib.Path(t["local"]) for t in reversed(info.get("traces", []))), None)
    if dev and dev.exists():
        integ.update(device_bytes=dev.stat().st_size, device_sha256=sha(dev))
    if raw.exists():
        integ.update(raw_bytes=raw.stat().st_size, raw_sha256=sha(raw))
        if "device_sha256" in integ:
            integ["device_eq_raw"] = integ["device_sha256"] == integ["raw_sha256"]
    if tarp.exists():
        with tarfile.open(tarp) as t:
            integ["tar_members"] = [(m.name, m.size) for m in t.getmembers()]
            if strict and (len(integ["tar_members"]) != len(BUNDLE_MEMBERS)
                           or {m.name for m in t.getmembers() if m.isfile()} != BUNDLE_MEMBERS):
                sys.exit(f"unexpected Perfetto bundle members: {integ['tar_members']}")
            t.extractall(d / "tar", filter="data")
        if raw.exists():
            integ["tar_trace_eq_raw"] = sha(d / "tar" / "trace.perfetto") == integ["raw_sha256"]
        integ["empty_sidecars"] = [n for n in ("symbols.pb", "deobfuscation.pb")
                                   if (d / "tar" / n).exists() and (d / "tar" / n).read_bytes() == b"\n\x00"]
        integ["default_eq_processed"] = dflt.exists() and sha(dflt) == sha(tarp)
    size = att.get("size_bytes")
    if size is not None and "raw_bytes" in integ:  # the only device-side check for built-in commands
        integ["attachment_size_eq_raw"] = int(size) == integ["raw_bytes"]
    summary["integrity"] = integ
    summary["backend_status"] = backend["status"]
    summary["downloads"] = {k: {kk: vv for kk, vv in v.items() if kk != "file"} for k, v in backend["downloads"].items()}

    summary["analyses"] = {}
    if strict:
        if (backend["status"].get("processing_state") != "ready"
                or not all(integ.get(k) for k in ("attachment_size_eq_raw", "tar_trace_eq_raw", "default_eq_processed"))):
            sys.exit(f"expected ready, byte-identical Perfetto downloads; see {d / 'backend.json'}: {integ}")
        summary["analyses"]["heap_profile_samples"] = heap_profile_evidence(raw, tarp)
    elif CELLS[a.type]["kind"] != "heap_graph":  # frame naming per module, before vs after processing
        for label, p in (("raw", raw), ("processed", tarp)):
            if p.exists():
                outp = d / f"analysis_{label}.json"
                subprocess.run([sys.executable, str(HERE / "analyze_frames.py"), str(p), "--out", str(outp)],
                               check=True, capture_output=True)
                summary["analyses"][label] = str(outp)
    if tarp.exists() and not strict:  # qualitative triage with the perfetto skill's own queries
        outp = d / "perfetto_triage.json"
        subprocess.run([sys.executable, str(HERE / "perfetto_triage.py"), str(tarp), "--type", a.type, "--out", str(outp)],
                       check=True, capture_output=True)
        summary["analyses"]["perfetto_triage"] = str(outp)
    (d / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps({k: summary[k] for k in ("cell", "session_id", "artifact_id", "app_version_code",
                                              "command_result", "integrity")}, indent=1))
    diags = (summary["backend_status"].get("diagnostics") or {})
    print("diagnostics:", json.dumps({k: v for k, v in diags.items() if v}, indent=1))
    bad = [k for k in ("device_eq_raw", "attachment_size_eq_raw", "tar_trace_eq_raw", "default_eq_processed")
           if integ.get(k) is False]
    if bad:
        sys.exit(f"INTEGRITY FAILURE: {bad}")


if __name__ == "__main__":
    main()
