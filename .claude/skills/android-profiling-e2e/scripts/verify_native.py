#!/usr/bin/env python3
"""Symbolize one library's frames locally with llvm-symbolizer and the same debug file that was
uploaded, and compare with the names the trace carries (symbols.pb in a processed TAR).

    verify_native.py <trace or tar> <build_id> <debug_file> [--out verify_native_<lib>.json]

Get build IDs and matching debug files from apk_build_ids.py. Libraries stored uncompressed in the
APK show up in the trace as a `.../base.apk` mapping carrying the library's build ID. A mismatch that differs only in
how a clone suffix is written (`foo.123` vs `foo (.123)`) is counted separately as `format_only`.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import find_llvm_symbolizer, tp  # noqa: E402


def norm(s):
    return s.replace(" ", "")


def suffix_norm(s):
    return re.sub(r"\s*\((\.\d+)\)$", r"\1", s).replace(" ", "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("build_id")
    ap.add_argument("debug_file")
    ap.add_argument("--out")
    a = ap.parse_args()
    rows = tp(a.trace, f"""select f.id, f.rel_pc,
        (select group_concat(s.name, ' <- ') from (select name from stack_profile_symbol s
           where s.symbol_set_id = f.symbol_set_id order by s.id) s) as backend, f.name raw
        from stack_profile_frame f join stack_profile_mapping m on f.mapping = m.id
        where m.build_id = '{a.build_id}'""")
    if not rows:
        mappings = tp(a.trace, "select distinct name, build_id from stack_profile_mapping where build_id is not null")
        print(json.dumps({"frames": 0, "note": f"no frames for build ID {a.build_id} in this trace: the library "
                          "didn't run during the capture, or the ID is wrong. Libraries loaded from the APK appear "
                          "as '.../base.apk' with their build ID."}))
        print("build IDs present:", sorted({m["build_id"] for m in mappings if m["build_id"]})[:20])
        return
    sym = find_llvm_symbolizer()
    p = subprocess.run([sym, "--obj", a.debug_file, "--demangle", "--inlines", "--functions=linkage",
                        "--output-style=JSON"], input="\n".join(hex(int(r["rel_pc"])) for r in rows),
                       capture_output=True, text=True)
    local = [json.loads(l) for l in p.stdout.splitlines() if l.strip()]
    res = {"frames": len(rows), "trace_named": 0, "local_named": 0, "innermost_match": 0, "format_only": 0,
           "mismatch": 0, "mismatch_examples": [], "format_only_examples": []}
    for r, l in zip(rows, local):
        names = [s.get("FunctionName", "") for s in l.get("Symbol", []) if s.get("FunctionName") not in ("", "??")]
        trace_names = [x.strip() for x in (r["backend"] or r["raw"] or "").split(" <- ") if x.strip()]
        res["trace_named"] += bool(trace_names)
        res["local_named"] += bool(names)
        if trace_names and names and norm(trace_names[0]) == norm(names[0]):
            res["innermost_match"] += 1
        elif trace_names and names and suffix_norm(trace_names[0]) == suffix_norm(names[0]):
            res["format_only"] += 1
            if len(res["format_only_examples"]) < 4:
                res["format_only_examples"].append({"trace": trace_names[0], "llvm_symbolizer": names[0]})
        else:
            res["mismatch"] += 1
            if len(res["mismatch_examples"]) < 8:
                res["mismatch_examples"].append({"rel_pc": r["rel_pc"], "trace": trace_names[:2], "llvm_symbolizer": names[:2]})
    out = pathlib.Path(a.out or pathlib.Path(a.trace).with_name(f"verify_native_{a.build_id[:8]}.json"))
    out.write_text(json.dumps(res, indent=2))
    print(json.dumps({k: v for k, v in res.items() if "examples" not in k}))
    for e in res["mismatch_examples"][:4]:
        print("MISMATCH", e)
    if res["frames"] and not res["local_named"]:
        print("llvm-symbolizer named nothing: is the debug file stripped or for another build ID?")


if __name__ == "__main__":
    main()
