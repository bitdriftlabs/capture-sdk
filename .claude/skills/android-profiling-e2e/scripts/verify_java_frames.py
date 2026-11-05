#!/usr/bin/env python3
"""Check every Java frame's deobfuscated name in a trace against the exact R8 mapping.txt, with a
separate tally for JIT-cache frames, and measure method ambiguity.

Per frame (stack_profile_frame row whose class appears in the mapping):
  correct             every backend candidate (split on '|') is a mapping entry for that obfuscated method
  wrong               some candidate is not
  missing             no deobfuscated_name, although the mapping renames the class or the method
  identity_unchanged  no deobfuscated_name, and the mapping keeps both class and method name: already readable
  unverifiable        the mapping has no entry for this method, so the backend's name can't be checked
Frames whose class is not in the mapping (framework, boot classpath) must stay undeobfuscated.

Ambiguity: R8 lists every method inlined into an obfuscated method under the same name. Lines that
share one `a:b:` line range form an inline group whose LAST line is the outer method, i.e. the one a
profiler frame actually is. `outer_only` counts how many candidates would remain if only those were
kept. That is this script's computation, not backend behavior.

    verify_java_frames.py <trace or tar> <mapping.txt> [--out verify_java_frames.json]
"""
import argparse
import json
import pathlib
import re
import statistics
import sys
from collections import defaultdict

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import tp  # noqa: E402

CLASS_RE = re.compile(r"^(\S+) -> (\S+):$")
METH_RE = re.compile(r"^\s+(?:(\d+:\d+):)?\S+\s+([^\s(]+)\(.*\)(?::[\d:]+)?\s+->\s+(\S+)$")


def load_mapping(path):
    cls = {}                                             # obf class -> original class
    groups = defaultdict(lambda: defaultdict(list))      # (obf class, obf method) -> group -> [qualified originals]
    cur = orig = None
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if line.startswith("#"):
                continue
            line = line.rstrip("\n")
            m = CLASS_RE.match(line)
            if m:
                orig, cur = m.group(1), m.group(2)
                cls[cur] = orig
                continue
            m = METH_RE.match(line)
            if m and cur:
                rng, name, obf = m.groups()
                q = name if "." in name else f"{orig}.{name}"
                groups[(cur, obf)][rng or ("no-range", q)].append(q)
    return cls, groups


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("mapping")
    ap.add_argument("--out")
    a = ap.parse_args()
    cls, groups = load_mapping(a.mapping)
    rows = tp(a.trace, """select f.id, f.name raw, f.deobfuscated_name deobf, m.name mapping
                          from stack_profile_frame f join stack_profile_mapping m on f.mapping = m.id
                          where f.name is not null and f.name like '%.%'""")

    def tally():
        return defaultdict(int)
    t = {"all": tally(), "jit": tally()}
    amb = {"all": {"backend": [], "outer": []}, "jit": {"backend": [], "outer": []}}
    examples = {"wrong": [], "missing": [], "unverifiable": [], "deobf_but_not_in_mapping": [], "ambiguous": []}
    for r in rows:
        raw, deobf = r["raw"], r["deobf"]
        oc, om = raw.rsplit(".", 1)
        scopes = ["all"] + (["jit"] if "jit" in (r["mapping"] or "") else [])
        orig_cls = cls.get(oc)
        if orig_cls is None:
            verdict = "deobf_but_not_in_mapping" if deobf else "not_in_mapping"
        else:
            g = groups.get((oc, om), {})
            expected = {q for v in g.values() for q in v}
            if not deobf:
                # R8 kept both names: the frame is already readable, and the backend rightly leaves it alone
                verdict = "identity_unchanged" if f"{oc}.{om}" in expected and orig_cls == oc else "missing"
            elif not expected:
                verdict = "unverifiable"  # no mapping evidence for this method
            else:
                cands = {c.strip() for c in deobf.split("|")}
                ok = cands <= expected
                verdict = "correct" if ok else "wrong"
                outer = {v[-1] for v in g.values()}
                for sc in scopes:
                    amb[sc]["backend"].append(len(cands))
                    amb[sc]["outer"].append(len(outer))
                if ok and len(cands) > 1 and len(outer) == 1 and len(examples["ambiguous"]) < 6:
                    examples["ambiguous"].append({"raw": raw, "candidates": len(cands),
                                                  "first_shown": sorted(cands)[0], "outer": next(iter(outer))})
        for s in scopes:
            t[s]["frames"] += 1
            t[s][verdict] += 1
        if verdict in examples and len(examples[verdict]) < 10:
            examples[verdict].append({"raw": raw, "backend": deobf, "mapping_class": orig_cls})

    def amb_summary(x):
        b, o = x["backend"], x["outer"]
        if not b:
            return {}
        return {"frames": len(b), "single_today": sum(n == 1 for n in b),
                "single_today_pct": round(100 * sum(n == 1 for n in b) / len(b)),
                "median": statistics.median(b), "mean": round(statistics.mean(b), 1), "max": max(b),
                "single_outer_only": sum(n == 1 for n in o),
                "single_outer_only_pct": round(100 * sum(n == 1 for n in o) / len(o)),
                "median_outer_only": statistics.median(o)}

    res = {"trace": a.trace, "mapping": a.mapping, "frames": dict(t["all"]), "jit_frames": dict(t["jit"]),
           "ambiguity": {"all": amb_summary(amb["all"]), "jit": amb_summary(amb["jit"])}, "examples": examples}
    out = pathlib.Path(a.out or pathlib.Path(a.trace).with_name("verify_java_frames.json"))
    out.write_text(json.dumps(res, indent=2))
    print("frames    ", json.dumps(res["frames"]))
    print("jit frames", json.dumps(res["jit_frames"]) if res["jit_frames"] else "0 (cell did not exercise JIT)")
    print("ambiguity ", json.dumps(res["ambiguity"]["all"]))
    for k in ("wrong", "missing", "unverifiable", "deobf_but_not_in_mapping"):
        for e in examples[k][:3]:
            print(k.upper(), e)


if __name__ == "__main__":
    main()
