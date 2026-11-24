#!/usr/bin/env python3
"""Write the Workflow + WorkflowMetadata JSON for one cell's temporary workflow.

    make_workflow.py --cell R-HPJ --type heap_profile_java --out .tmp/.../wf [--arg duration_ms=8000]

Then: bd workflow create <out>/<cell>.workflow.json --metadata-file <out>/<cell>.metadata.json --deploy -ojson
      (the new workflow ID is at `.create.id` in that output)
Run check_commands.py first: the backend rejects arguments the command's registration doesn't define.
"""
import argparse
import datetime
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import CELLS, PKG, STRICT_WORKFLOW_CELLS, TRIGGERS, command_name  # noqa: E402


def parse_override(s, current):
    k, v = s.split("=", 1)
    kind = next(iter(current.get(k, {"sint_data": 0})))
    if kind == "bool_data":
        return k, {"bool_data": v.lower() in ("1", "true", "yes")}
    if kind == "string_data":
        return k, {"string_data": v}
    return k, {"sint_data": str(int(v))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cell", required=True)
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--out", required=True)
    ap.add_argument("--trigger", choices=sorted(TRIGGERS),
                    help="use another trigger button than the cell default, e.g. when a live workflow you may not "
                         "stop reacts to the default one. Pass the same value to make_workflow.py and run_cell.py")
    ap.add_argument("--app-id", default=PKG)
    ap.add_argument("--marker", help="require this workflow_e2e_run global field on the trigger")
    ap.add_argument("--arg", action="append", default=[], help="override a command argument, key=value")
    ap.add_argument("--defaults", action="store_true", help="omit arguments to exercise built-in server defaults")
    a = ap.parse_args()
    if a.defaults and (a.arg or a.type not in STRICT_WORKFLOW_CELLS):
        ap.error("--defaults is only for the built-in system trace, heap dump, and heap profile cells and cannot be combined with --arg")

    cell = CELLS[a.type]
    args = dict(cell["args"])
    for o in a.arg:
        k, v = parse_override(o, args)
        args[k] = v
    cmd = cell["command"]
    selector = ({"builtin_command": {"type": cmd}} if isinstance(cmd, int)
                else {"registered_command": {"registered_command_id": cmd}})
    if not a.defaults:
        selector["arguments"] = args
    tag = a.cell.lower().replace(" ", "-")
    trigger = a.trigger or cell["trigger"]
    trig = TRIGGERS[trigger]
    stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")

    matcher = {"base_matcher": {"log_body": True, **trig}}
    if a.marker:
        matcher = {"and_matcher": {"matchers": [matcher, {"base_matcher": {
            "log_field": "workflow_e2e_run", "operator": "EQUAL", "string_value": a.marker,
        }}]}}

    workflow = {
        "name": f"android-profiling-e2e tmp {a.cell} {stamp}",
        "flows": [{"exclusive": {}, "steps": [
            {"match_rule": {"match_id": f"{tag}-trigger",
                            "generic_match": matcher},
             "exit_conditions": [], "save_fields": []},
            {"match_rule": {"match_id": f"{tag}-command",
                            "match_run_command": {"command_selector": selector,
                                                  "minimum_execution_interval": cell["interval"],
                                                  "outcome_log_matcher": {"base_matcher": {
                                                      "log_body": True, "operator": "EQUAL",
                                                      "string_value": "Command completed",
                                                  }}}},
             "exit_conditions": [], "save_fields": []},
        ]}],
        "actions": [{"rule_id": f"{tag}-capture",
                     "flush_rule": {"id": f"{tag}-capture", "applied_daily_limit": 100,
                                    "match_id": f"{tag}-command"}}],
        "platform_targets": [{"android": {"apps": [{"app_id": a.app_id}]}}],
        "group_by_fields": [],
    }
    metadata = {
        "description": (f"Temporary android-profiling-e2e workflow for cell {a.cell} ({cell['label']}). "
                        f"Tapping '{trigger}' in the gradle-test-app runs {command_name(a.type)} and "
                        "captures the session. Stopped after the test; safe to delete."),
        "per_rule_metadata": [
            {"rule_id": f"{tag}-trigger", "title": f"{trigger} tapped"},
            {"rule_id": f"{tag}-command", "title": f"Run {command_name(a.type)}"},
            {"rule_id": f"{tag}-capture", "title": "Capture session"},
        ],
    }
    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{a.cell}.workflow.json").write_text(json.dumps(workflow, indent=2))
    (out / f"{a.cell}.metadata.json").write_text(json.dumps(metadata, indent=2))
    print(out / f"{a.cell}.workflow.json")
    print(out / f"{a.cell}.metadata.json")


if __name__ == "__main__":
    main()
