#!/usr/bin/env python3
"""Preflight: check that every planned cell's command exists, is enabled, and that its arguments
match the definition the bitdrift account holds for it.

The backend validates a workflow's command arguments against that definition, not against the app's
code, so `bd workflow create` rejects an argument the definition doesn't list ("unknown argument X
for command Y"), and a required one that is missing.

- Registered commands (the gradle-test-app's demo commands) are defined per account with
  `bd command register`. On a mismatch this prints the register call that would fix it, keeping
  existing definitions. Registering changes a shared account definition: run it only with the
  user's OK, or drop the argument with `make_workflow.py --drop-arg` and note the lost coverage.
- Built-in commands (well-known types, e.g. 3 = STACK_SAMPLING_TRACE) have fixed definitions; they
  can only be enabled or disabled (`bd command enable/disable`, also an account change).

    check_commands.py --types heap_dump,heap_profile_java,stack_sampling
"""
import argparse
import json
import pathlib
import shlex
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import CELLS  # noqa: E402

TYPE_OF = {"sint_data": "INT64", "bool_data": "BOOL", "string_data": "STRING"}


def definitions():
    r = subprocess.run(["bd", "command", "list", "-ojson"], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"bd command list failed: {r.stderr.strip()[:300]}")
    reg, builtin = {}, {}
    for c in json.loads(r.stdout).get("commands", []):
        if "registered" in c:
            meta = c["registered"].get("metadata") or {}
            reg[meta.get("key")] = {"meta": meta, "enabled": c.get("enabled")}
        elif "well_known" in c:
            wk = c["well_known"]
            info = {"meta": wk.get("metadata") or {}, "enabled": c.get("enabled")}
            builtin[wk.get("type")] = info
            builtin[info["meta"].get("key")] = info
    return reg, builtin


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--types", required=True, help="comma-separated cell types")
    a = ap.parse_args()
    reg, builtin = definitions()
    problems = 0
    for t in (x.strip() for x in a.types.split(",")):
        cell = CELLS[t]
        cmd = cell["command"]
        is_builtin = isinstance(cmd, int)
        entry = builtin.get(cmd) if is_builtin else reg.get(cmd)
        label = f"built-in type {cmd}" if is_builtin else f"'{cmd}'"
        if entry is None:
            problems += 1
            print(f"{t}: command {label} is not defined in this account")
            continue
        if entry["enabled"] is False:
            problems += 1
            fix = f"bd command enable {entry['meta'].get('key', cmd)}"
            print(f"{t}: command {label} is disabled. Enabling it is an account change (user's OK): {fix}")
        defs = entry["meta"].get("argument_definitions", [])
        have = {d["name"] for d in defs}
        unknown = [k for k in cell["args"] if k not in have]
        required_unsent = [d["name"] for d in defs if not d.get("optional") and d["name"] not in cell["args"]]
        if not unknown and not required_unsent:
            if entry["enabled"] is not False:
                print(f"{t}: {label} OK, enabled, arguments {sorted(have)}")
            continue
        problems += 1
        if required_unsent:
            print(f"{t}: the definition requires arguments the cell doesn't send: {required_unsent}")
        if unknown:
            print(f"{t}: arguments the definition of {label} doesn't list: {unknown}")
            if is_builtin:
                print("  built-in definitions are fixed: fix the cell's arguments in common.py")
                continue
            parts = ["bd", "command", "register", cmd, "--description", entry["meta"].get("description") or cmd,
                     "--platform", "android"]
            for d in list(defs) + [{"name": k, "type": TYPE_OF[next(iter(cell["args"][k]))], "description": k,
                                    "optional": True} for k in unknown]:
                parts += ["--argument-definition", json.dumps({k: d[k] for k in ("name", "type", "description", "optional") if k in d})]
            print("  fix (shared account change, needs the user's OK):\n   ", " ".join(shlex.quote(p) for p in parts))
            print("  or run without them: make_workflow.py ... " + " ".join(f"--drop-arg {k}" for k in unknown))
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
