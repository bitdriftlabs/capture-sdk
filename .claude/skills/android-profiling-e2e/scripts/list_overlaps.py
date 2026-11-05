#!/usr/bin/env python3
"""Preflight: find LIVE workflows that could interfere with a run on this app.

`bd workflow list` returns at most 100 workflows per call, so this pages through the whole account.
A workflow is reported when it is LIVE, targets the app on Android (or every Android app), and either
matches the same trigger log body as a planned cell, or runs any device command (which competes for
the platform profiler: Android runs one profiling session per app at a time).

    list_overlaps.py --types stack_sampling[,heap_dump] [--app-id io.bitdrift.gradletestapp] [--ignore euZX]
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import CELLS, PKG, TRIGGERS  # noqa: E402

SAMPLE_BODIES = {"Log JSON Field": "JSON field to match upon.", "Log Single Message": "Log message with level: Info"}


def page(offset):
    r = subprocess.run(["bd", "workflow", "list", "--limit", "100", "--offset", str(offset), "-ojson"],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"bd workflow list failed: {r.stderr.strip()[:300]}")
    return json.loads(r.stdout)


def targets_app(wf, app):
    for t in wf.get("platform_targets") or []:
        apps = (t.get("android") or {}).get("apps")
        if apps is None:
            continue
        if not apps or any(x.get("app_id") == app for x in apps):
            return True
    return False


def matchers(obj):
    if isinstance(obj, dict):
        bm = obj.get("base_matcher")
        if isinstance(bm, dict) and bm.get("log_body"):
            yield bm
        for v in obj.values():
            yield from matchers(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from matchers(v)


def hits(bm, body):
    op, val = bm.get("operator"), bm.get("string_value") or ""
    try:
        return (op == "EQUAL" and val == body) or (op == "REGEX" and re.search(val, body)) or \
               (op in ("CONTAINS", "STARTS_WITH") and val and val in body)
    except re.error:
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--types", required=True)
    ap.add_argument("--app-id", default=PKG)
    ap.add_argument("--ignore", default="", help="comma-separated workflow ids to skip (your own temp workflows)")
    a = ap.parse_args()
    bodies = {CELLS[t.strip()]["trigger"] for t in a.types.split(",")}
    ignore = set(filter(None, a.ignore.split(",")))
    found, offset, total = [], 0, None
    while total is None or offset < total:
        d = page(offset)
        total = d.get("total_filtered_workflows") or d.get("total_workflows") or 0
        items = d.get("items", [])
        if not items:
            break
        for it in items:
            wf = it.get("workflow", {})
            if wf.get("state") != "LIVE" or wf.get("id") in ignore or not targets_app(wf, a.app_id):
                continue
            s = json.dumps(wf.get("flows"))
            trig = [b for b in bodies if any(hits(m, SAMPLE_BODIES[b]) for m in matchers(wf.get("flows")))]
            cmd = "match_run_command" in s
            if trig or cmd:
                found.append({"id": wf["id"], "name": wf.get("name"), "same_trigger": trig, "runs_command": cmd})
        offset += len(items)
    interfering = [f for f in found if f["runs_command"]]
    harmless = [f for f in found if not f["runs_command"]]
    print(json.dumps({
        "scanned": offset,
        # run a device command for this app: they can grab the profiler or fire on your trigger. Propose stopping them.
        "interfering": interfering,
        # match the same trigger log but only chart/capture: they fire too, but don't affect the test.
        "also_fire_harmless": [f"{f['id']} {f['name']}" for f in harmless],
    }, indent=1))


if __name__ == "__main__":
    main()
