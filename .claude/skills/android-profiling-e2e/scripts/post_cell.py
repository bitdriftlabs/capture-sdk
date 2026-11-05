#!/usr/bin/env python3
"""After run_cell.py: find the session and attachment, download raw / processed / default plus
/status, check byte integrity, unpack the TAR, and run the trace_processor summaries.

    post_cell.py --cell R-HPJ --type heap_profile_java --runs .tmp/.../runs [--workflow <id>]

--workflow is needed when there is no on-device copy (built-in commands): the session is then the
newest one the temp workflow captured.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
import tarfile
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import CELLS, command_name  # noqa: E402


def sha(p):
    return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()


def bd_json(*args):
    r = subprocess.run(["bd", *args, "-ojson"], capture_output=True, text=True)
    try:
        return json.loads(r.stdout or "{}")
    except json.JSONDecodeError:
        return {}


def find_session(info, workflow):
    for t in reversed(info.get("traces", [])):
        if t.get("session_id") not in (None, "null"):
            return t["session_id"]
    for inv in reversed(info.get("invocations", [])):
        if inv.get("session_id") not in (None, "null"):
            return inv["session_id"]
    if workflow:
        sessions = bd_json("workflow", "captured-sessions", workflow, "--last", "2h").get("captured_sessions", [])
        sessions.sort(key=lambda s: s.get("last_seen") or "", reverse=True)
        if sessions:
            return sessions[0]["session_id"]
    sys.exit("no session id: pass --workflow <temp workflow id>")


def find_completion(d, sid, name, attempts=12):
    for _ in range(attempts):
        logs = bd_json("timeline", "logs", sid).get("logs", [])
        (d / "timeline.json").write_text(json.dumps({"logs": logs}))
        done = [l for l in logs if l.get("message") == "Command completed" and l.get("command_attachment")]
        named = [l for l in done if name in json.dumps(l)]
        if named or done:
            return (named or done)[-1]
        time.sleep(20)
    sys.exit(f"no 'Command completed' log with an attachment in session {sid}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cell", required=True)
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--runs", required=True)
    ap.add_argument("--workflow")
    a = ap.parse_args()
    d = pathlib.Path(a.runs) / a.cell
    info = json.loads((d / "cell.json").read_text())
    sid = find_session(info, a.workflow)
    done = find_completion(d, sid, command_name(a.type))
    att = done["command_attachment"]
    fields = (done.get("fields") or {}).get("fields", {})
    pick = lambda k: (fields.get(k) or {}).get("string_data")
    summary = {"cell": a.cell, "type": a.type, "session_id": sid, "artifact_id": att["artifact_id"],
               "attachment": att, "completed_at": done.get("timestamp"),
               "app_version_code": pick("_app_version_code"), "command_result": pick("_result"),
               "command_details": pick("_command_details")}

    subprocess.run([sys.executable, str(HERE / "fetch_backend.py"), att["artifact_id"], "--out", str(d)], check=True)
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
    if CELLS[a.type]["kind"] != "heap_graph":  # frame naming per module, before vs after processing
        for label, p in (("raw", raw), ("processed", tarp)):
            if p.exists():
                outp = d / f"analysis_{label}.json"
                subprocess.run([sys.executable, str(HERE / "analyze_frames.py"), str(p), "--out", str(outp)],
                               check=True, capture_output=True)
                summary["analyses"][label] = str(outp)
    if tarp.exists():  # qualitative triage with the perfetto skill's own queries
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
        print("INTEGRITY FAILURE:", bad)


if __name__ == "__main__":
    main()
