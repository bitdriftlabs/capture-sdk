#!/usr/bin/env python3
"""Process a cell's raw trace with Perfetto's own `trace_processor bundle`, using the same mapping and
debug files the backend got, then compare that baseline with the backend's processed TAR frame by
frame (or class by class for heap dumps).

    baseline_compare.py --cell-dir .tmp/.../runs/R-HPJ --type heap_profile_java \
        [--mapping build/release/mapping.txt] [--symbols build/release/dif] [--app-id io.bitdrift.gradletestapp]

Writes <cell-dir>/trace_processor.tar and <cell-dir>/baseline_compare.json. Each difference is
labelled bitdrift_only or trace_processor_only, so every finding can be classified as "bitdrift
only", "same in trace_processor" or "bitdrift better".
"""
import argparse
import json
import pathlib
import subprocess
import sys
import tarfile
from collections import Counter

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import CELLS, PKG, TP, tp  # noqa: E402

FRAMES_Q = """select f.id, f.name raw, f.deobfuscated_name deobf, m.name mapping,
  (select group_concat(s.name, ' <- ') from (select name from stack_profile_symbol s
     where s.symbol_set_id = f.symbol_set_id order by s.id) s) sym
  from stack_profile_frame f join stack_profile_mapping m on f.mapping = m.id"""
PROC_Q = """select group_concat(distinct coalesce(name, 'pid=' || pid)) names from process
  where upid in (select upid from heap_profile_allocation union select upid from heap_graph_object
                 union select t.upid from perf_sample s join thread t using(utid))"""
LOSS_Q = "select name, value from stats where value > 0 and severity in ('data_loss', 'error')"


def sidecars(tar):
    with tarfile.open(tar) as t:
        return {m.name: m.size for m in t.getmembers()}


def cmp_frames(bitdrift, baseline):
    o = {r["id"]: r for r in tp(bitdrift, FRAMES_Q)}
    u = {r["id"]: r for r in tp(baseline, FRAMES_Q)}
    res = {"same_frame_ids": set(o) == set(u)}
    for scope, pred in (("java", lambda r: "." in (r["raw"] or "")), ("jit", lambda r: "jit" in (r["mapping"] or ""))):
        c, ex = Counter(), []
        for i, r in o.items():
            if i not in u or not pred(r):
                continue
            a, b = r["deobf"], u[i]["deobf"]
            k = ("identical" if a == b else "bitdrift_only" if a and not b else "trace_processor_only" if b and not a else "different")
            if k == "trace_processor_only" and b == r["raw"]:
                k = "trace_processor_identity_noop"
            c[k] += 1
            if k not in ("identical", "trace_processor_identity_noop") and len(ex) < 6:
                ex.append({"raw": r["raw"], "bitdrift": (a or "")[:200], "trace_processor": (b or "")[:200]})
        res[scope] = dict(c)
        res[f"{scope}_examples"] = ex
    c, ex = Counter(), []
    for i, r in o.items():
        if i not in u or not (r["sym"] or u[i]["sym"]):
            continue
        a, b = r["sym"] or "", u[i]["sym"] or ""
        k = "identical" if a == b else "bitdrift_only" if a and not b else "trace_processor_only" if b and not a else "different"
        c[k] += 1
        if k != "identical" and len(ex) < 6:
            ex.append({"rel_pc": r.get("raw"), "bitdrift": a[:160], "trace_processor": b[:160]})
    res["native_symbols"], res["native_examples"] = dict(c), ex
    return res


def cmp_heap(bitdrift, baseline):
    q = "select id, name, deobfuscated_name d, location from heap_graph_class"
    o = {r["id"]: r for r in tp(bitdrift, q)}
    u = {r["id"]: r for r in tp(baseline, q)}
    c, ex = Counter(), []
    for i in o:
        if i not in u:
            continue
        a, b = o[i]["d"], u[i]["d"]
        k = "identical" if a == b else "bitdrift_only" if a and not b else "trace_processor_only" if b and not a else "different"
        if k == "trace_processor_only" and b == o[i]["name"]:
            k = "trace_processor_identity_noop"  # trace_processor "deobfuscated" a non-app class to its own name
        c[k] += 1
        if k not in ("identical", "trace_processor_identity_noop") and len(ex) < 12:
            ex.append({"name": o[i]["name"], "location": o[i]["location"], "bitdrift": a, "trace_processor": b})
    qf = "select distinct field_name f, deobfuscated_field_name d from heap_graph_reference where field_name is not null"
    of = {r["f"]: r["d"] for r in tp(bitdrift, qf)}
    uf = {r["f"]: r["d"] for r in tp(baseline, qf)}
    cf = Counter(("identical" if of[k] == uf.get(k) else "different") for k in of)
    return {"classes": dict(c), "class_examples": ex, "fields": dict(cf)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cell-dir", required=True)
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--mapping")
    ap.add_argument("--symbols", help="directory with the native debug files")
    ap.add_argument("--app-id", default=PKG)
    a = ap.parse_args()
    d = pathlib.Path(a.cell_dir)
    raw, bitdrift, baseline = d / "backend_raw.perfetto-trace", d / "backend_processed.tar", d / "trace_processor.tar"
    cmd = [TP, "bundle", "--quiet", "--no-auto-symbol-paths", "--no-auto-proguard-maps"]
    if a.mapping:
        cmd += ["--proguard-map", f"{a.app_id}={a.mapping}"]
    if a.symbols:
        cmd += ["--symbol-paths", a.symbols]
    subprocess.run(cmd + [str(raw), str(baseline)], check=True)

    res = {"trace_processor_cmd": " ".join(cmd[1:]), "sidecar_bytes": {"bitdrift": sidecars(bitdrift), "trace_processor": sidecars(baseline)},
           "process": {"bitdrift": tp(bitdrift, PROC_Q), "trace_processor": tp(baseline, PROC_Q)},
           "loss_stats": {"bitdrift": tp(bitdrift, LOSS_Q), "trace_processor": tp(baseline, LOSS_Q)}}
    res["diff"] = cmp_heap(bitdrift, baseline) if CELLS[a.type]["kind"] == "heap_graph" else cmp_frames(bitdrift, baseline)
    if a.mapping:  # same correctness checks on the trace_processor bundle
        v = "verify_heap_dump.py" if CELLS[a.type]["kind"] == "heap_graph" else "verify_java_frames.py"
        outp = d / f"trace_processor_{v.replace('.py', '.json')}"
        subprocess.run([sys.executable, str(HERE / v), str(baseline), a.mapping, "--out", str(outp)], check=True,
                       capture_output=True)
        res["trace_processor_verify"] = json.loads(outp.read_text())
        res["trace_processor_verify"].pop("examples", None)
    (d / "baseline_compare.json").write_text(json.dumps(res, indent=2))
    print(json.dumps({k: v for k, v in res.items() if k != "trace_processor_cmd"}, indent=1)[:4000])


if __name__ == "__main__":
    main()
