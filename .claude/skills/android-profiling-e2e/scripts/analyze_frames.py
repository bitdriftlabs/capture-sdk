#!/usr/bin/env python3
"""Frame-naming coverage for a heap profile or stack sampling trace (raw .perfetto-trace or processed TAR).

This is the part the perfetto skill doesn't cover: per module, how many frames carry a name in the
trace itself, a native symbol from symbols.pb, or a Java deobfuscated name from deobfuscation.pb, and
how much of the weight (allocated bytes or samples) reaches a named app frame. Run it on the raw
trace and the processed TAR to see what processing added. For what the trace says about the app
(top allocation sites, hot code), use perfetto_triage.py. Only what is inside the file is used (no
debuginfod, no PERFETTO_BINARY_PATH).

    analyze_frames.py <trace> --out analysis.json
"""
import argparse
import json
import pathlib
import re
import sys
from collections import defaultdict

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import PKG, tp  # noqa: E402

APP_NATIVE = ("libcapture.so", "libgradletestapp_native.so")


def category(mapping, build_id):
    m = mapping or ""
    if any(m.endswith("/" + lib) for lib in APP_NATIVE):
        return "app-native:" + m.rsplit("/", 1)[-1]
    if build_id and PKG in m and m.endswith(".apk"):
        # native library loaded straight from the APK: the mapping is base.apk, the build ID names the library
        return "app-native:build_id=" + build_id
    if "jit-cache" in m or "jit-code-cache" in m:
        return "java:jit-cache"
    if PKG in m and m.endswith((".apk", ".odex", ".vdex", ".oat")):
        return "java:app-apk/oat"
    if "/framework/" in m or m.endswith(".jar") or (m.endswith(".oat") and "boot" in m):
        return "java:framework"
    if "com.android.art" in m:
        return "art"
    if m.startswith("/vendor/"):
        return "vendor-native"
    if m.startswith(("/system/", "/apex/")):
        return "system-native"
    return "other"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    frames = tp(a.trace, """
        select f.id, f.name as raw_name, f.deobfuscated_name, f.rel_pc, m.name as mapping, m.build_id,
               (select s.name from stack_profile_symbol s where s.symbol_set_id = f.symbol_set_id
                order by s.id desc limit 1) as sym_name
        from stack_profile_frame f join stack_profile_mapping m on f.mapping = m.id""")
    callsites = {c["id"]: c for c in tp(a.trace, "select id, parent_id, frame_id from stack_profile_callsite")}
    meta = (tp(a.trace, """select
        (select count(*) from heap_profile_allocation) as alloc_rows,
        (select count(*) from perf_sample) as perf_samples,
        (select count(*) from stack_profile_frame) as frames,
        (select count(*) from stack_profile_symbol) as symbol_rows,
        (select count(*) from stack_profile_frame where deobfuscated_name is not null) as deobf_frames,
        (select group_concat(distinct coalesce(p.name, 'pid=' || p.pid)) from process p
           where p.upid in (select upid from heap_profile_allocation union select t.upid from perf_sample s
                            join thread t using(utid))) as processes""") or [{}])[0]
    if int(meta.get("alloc_rows") or 0):
        weight_kind = "alloc_bytes"
        weights = tp(a.trace, """select callsite_id, sum(case when size > 0 then size else 0 end) w
                                 from heap_profile_allocation group by 1""")
    else:
        weight_kind = "samples"
        weights = tp(a.trace, "select callsite_id, count(*) w from perf_sample where callsite_id is not null group by 1")
    stats = tp(a.trace, "select name, idx, severity, source, value from stats where value > 0 and "
                        "(severity in ('error', 'data_loss') or name like 'heapprofd%' or name like '%symbol%' "
                        "or name like '%deobf%' or name like '%perf%')")

    F = {}
    for f in frames:
        eff = f["deobfuscated_name"] or f["sym_name"] or f["raw_name"] or ""
        cands = [c.strip() for c in eff.split("|")] if f["deobfuscated_name"] else [eff]
        # A deobfuscated Java frame can list several candidate methods (R8 inlining). Count it as app
        # code only when every candidate is; show the first candidate plus how many others there are.
        label = cands[0] + (f"  (+{len(cands) - 1} candidates)" if len(cands) > 1 else "")
        F[f["id"]] = dict(f, cat=category(f["mapping"], f["build_id"]), eff=eff, label=label,
                          app_all=all("io.bitdrift" in c for c in cands), app_any=any("io.bitdrift" in c for c in cands))
    by_mod = defaultdict(lambda: defaultdict(int))
    for f in F.values():
        d = by_mod[f["cat"]]
        d["frames"] += 1
        d["named_in_trace"] += bool(f["raw_name"])
        d["native_symbol_from_sidecar"] += bool(f["sym_name"])
        d["java_deobfuscated"] += bool(f["deobfuscated_name"])
        d["named_effective"] += bool(f["eff"])

    chain_cache = {}

    def chain(cid):
        if cid not in chain_cache:
            out, cur = [], cid
            while cur and cur in callsites:
                out.append(callsites[cur]["frame_id"])
                cur = callsites[cur]["parent_id"]
            chain_cache[cid] = out
        return chain_cache[cid]

    total, cov = 0, defaultdict(int)
    for row in weights:
        w = int(row["w"] or 0)
        if w <= 0:
            continue
        total += w
        fr = [F[x] for x in chain(row["callsite_id"]) if x in F]
        if any(f["cat"].startswith("app-native") for f in fr):
            cov["with_app_native_frame"] += w
        if any(f["app_all"] for f in fr):
            cov["with_io_bitdrift_frame"] += w
        if any(f["app_any"] for f in fr):
            cov["with_io_bitdrift_frame_any_candidate"] += w  # upper bound, inflated by ambiguity
        if fr and fr[0]["eff"]:
            cov["with_named_leaf"] += w

    short = re.compile(r"^(?:[a-zA-Z0-9$]{1,3}\.)+[a-zA-Z0-9$<>]{1,3}$")  # R8-style a.b.c names
    result = {
        "trace": a.trace, "meta": meta, "weight": weight_kind, "total_weight": total, "stats": stats,
        "coverage": {k: {"weight": v, "pct": round(100 * v / total, 1) if total else 0} for k, v in cov.items()},
        "by_module": {k: dict(v) for k, v in sorted(by_mod.items())},
        "obfuscation_left": {
            "java_frames_r8_style_raw": sum(1 for f in F.values() if f["cat"].startswith("java:") and short.match(f["raw_name"] or "")),
            "java_frames_r8_style_effective": sum(1 for f in F.values() if f["cat"].startswith("java:") and short.match(f["eff"])),
        },
    }
    pathlib.Path(a.out).write_text(json.dumps(result, indent=2))
    print(json.dumps({k: result[k] for k in ("meta", "weight", "total_weight", "coverage", "obfuscation_left")}, indent=1))
    for k, v in result["by_module"].items():
        print(f"  {k:28} {dict(v)}")


if __name__ == "__main__":
    main()
