#!/usr/bin/env python3
"""Run the Perfetto skill's own triage queries on a processed TAR, for the qualitative part of the
report (what the trace says about the app, now that names are restored).

This deliberately reuses existing analysis instead of re-implementing it:
  heap dump            perfetto skill  workflows/android_memory/scripts/triage_dominator_path.sql
  heap profile, Java   perfetto skill  workflows/android_memory/scripts/triage_java_allocation.sql
  heap profile, native perfetto skill  workflows/android_memory/scripts/triage_native_heap.sql
  stack sampling       Perfetto stdlib `linux.perf.samples` (summary tree) + per-thread sample counts
For follow-up questions, use the perfetto skill's workflow docs (heap_dump.md, java_allocation_profile.md,
native_heap.md) or the android-profiler skill's analysis workflows (perfetto-sql, perfetto-trace-analysis).

    perfetto_triage.py <processed.tar> --type heap_profile_java --out triage.json
"""
import argparse
import csv
import io
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import CELLS, TP, tp  # noqa: E402

PERFETTO_SKILL = pathlib.Path(TP).resolve().parent.parent if TP else None
SCRIPTS = PERFETTO_SKILL / "workflows/android_memory/scripts" if PERFETTO_SKILL else None

# The perfetto triage SQL reports the top allocation *leaf*, which in Java profiles is usually an ART
# allocator frame. This adds the closest app frames by allocated bytes so the report can name app code.
APP_ALLOC = """INCLUDE PERFETTO MODULE android.memory.heap_profile.summary_tree;
    select name, max(cumulative_size) bytes from android_heap_profile_summary_tree
    where name like '%io.bitdrift%' group by name order by bytes desc limit 15"""

SQL_FILES = {
    "heap_dump": ["triage_dominator_path.sql"],
    "heap_profile_java": ["triage_java_allocation.sql"],
    "heap_profile_native": ["triage_native_heap.sql"],
}
HEAP_GRAPH_HEADLINE = """select count(*) objects, sum(self_size) self_bytes, count(distinct type_id) classes,
    (select group_concat(distinct coalesce(p.name, 'pid=' || p.pid)) from process p
       where p.upid in (select upid from heap_graph_object)) process from heap_graph_object"""
PERF_QUERIES = {
    "top_self": """INCLUDE PERFETTO MODULE linux.perf.samples;
        select name, sum(self_count) self_samples from linux_perf_samples_summary_tree
        group by name order by self_samples desc limit 15""",
    "top_cumulative_app": """INCLUDE PERFETTO MODULE linux.perf.samples;
        select name, max(cumulative_count) samples from linux_perf_samples_summary_tree
        where name like '%io.bitdrift%' or name like '%androidx.compose%' or name like '%capture%'
        group by name order by samples desc limit 15""",
    "threads": """select coalesce(t.name, case when t.tid = p.pid then '<main thread, unnamed>' else 'tid=' || t.tid end) thread,
        count(*) samples from perf_sample s join thread t using(utid) left join process p using(upid)
        group by 1 order by 2 desc limit 12""",
}


def run_file(trace, sql_file):
    r = subprocess.run([TP, "query", "--no-agent", "--quiet", str(trace), "-f", str(sql_file)], capture_output=True, text=True)
    lines = r.stdout.splitlines()
    start = next((i for i, l in enumerate(lines) if l.startswith('"')), None)
    if start is None:
        return {"error": (r.stderr or r.stdout)[-400:]}
    return list(csv.DictReader(io.StringIO("\n".join(lines[start:]))))


def shorten(rows, limit=400):
    for r in rows if isinstance(rows, list) else []:
        for k, v in r.items():
            if isinstance(v, str) and len(v) > limit:
                r[k] = v[:limit] + f"... ({len(v)} chars)"
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("--type", required=True, choices=sorted(CELLS))
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    res = {"perfetto_skill": str(PERFETTO_SKILL)}
    if CELLS[a.type]["kind"] == "heap_graph":
        res["headline"] = tp(a.trace, HEAP_GRAPH_HEADLINE)
    if CELLS[a.type]["kind"] == "perf_sample":
        for name, sql in PERF_QUERIES.items():
            res[name] = tp(a.trace, sql)
    if CELLS[a.type]["kind"] == "heap_profile":
        res["top_app_allocators"] = shorten(tp(a.trace, APP_ALLOC))
    for f in SQL_FILES.get(a.type, []):
        path = SCRIPTS / f if SCRIPTS else None
        res[f] = shorten(run_file(a.trace, path)) if path and path.exists() else {"error": f"{f} not found under {SCRIPTS}"}
    pathlib.Path(a.out).write_text(json.dumps(res, indent=2))
    print(json.dumps(res, indent=1)[:3000])


if __name__ == "__main__":
    main()
