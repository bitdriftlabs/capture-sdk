#!/usr/bin/env python3
"""Check heap-graph deobfuscation (class names + reference field names) against the R8 mapping.txt.

Expected values come only from the mapping:
  class  obf -> orig from "orig -> obf:" lines ([] array suffixes and java.lang.Class<...> handled)
  field  "DeclObf.fieldObf" -> "DeclOrig.fieldOrig" from field lines (no parentheses); an original
         field name that is already qualified (moved by R8) is used as-is

    verify_heap_dump.py <trace or tar> <mapping.txt> [--out verify_heap_dump.json]
"""
import argparse
import json
import pathlib
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import tp  # noqa: E402

CLASS_RE = re.compile(r"^(\S+) -> (\S+):$")
FIELD_RE = re.compile(r"^\s+(\S+)\s+([^\s(]+)\s+->\s+(\S+)$")


def load_mapping(path):
    cls, fields, cur = {}, defaultdict(set), None
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if line.startswith("#"):
                continue
            line = line.rstrip("\n")
            m = CLASS_RE.match(line)
            if m:
                cls[m.group(2)], cur = m.group(1), m.group(2)
                continue
            m = FIELD_RE.match(line)
            if m and cur and "(" not in line:
                fields[(cur, m.group(3))].add(m.group(2))
    return cls, fields


def split_class(name):
    pre = suf = ""
    if name.startswith("java.lang.Class<") and name.endswith(">"):
        pre, name, suf = "java.lang.Class<", name[len("java.lang.Class<"):-1], ">"
    arr = ""
    while name.endswith("[]"):
        name, arr = name[:-2], arr + "[]"
    return pre, name, arr + suf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("mapping")
    ap.add_argument("--out")
    a = ap.parse_args()
    cls, fields = load_mapping(a.mapping)
    c, f = Counter(), Counter()
    ex = {"wrong_class": [], "missing_class": [], "wrong_field": [], "missing_field": []}

    for r in tp(a.trace, "select name, deobfuscated_name from heap_graph_class where location like '/data/app/%'"):
        pre, base, suf = split_class(r["name"])
        orig, got = cls.get(base), r["deobfuscated_name"]
        c["app_classes"] += 1
        if orig is None:
            c["not_in_mapping"] += 1
            c["deobf_but_not_in_mapping"] += bool(got)
            continue
        if orig == base:
            c["identity"] += 1
            if got and got != r["name"]:
                c["identity_but_renamed"] += 1
            continue
        c["renamed"] += 1
        exp = pre + orig + suf
        if got is None:
            c["renamed_missing"] += 1
            len(ex["missing_class"]) < 10 and ex["missing_class"].append({"name": r["name"], "expected": exp})
        elif got == exp:
            c["renamed_correct"] += 1
        else:
            c["renamed_wrong"] += 1
            len(ex["wrong_class"]) < 10 and ex["wrong_class"].append({"name": r["name"], "got": got, "expected": exp})

    for r in tp(a.trace, "select distinct field_name, deobfuscated_field_name from heap_graph_reference where field_name is not null"):
        name, got = r["field_name"], r["deobfuscated_field_name"]
        if not name or "." not in name:
            continue
        decl, fld = name.rsplit(".", 1)
        orig_cls = cls.get(decl)
        if orig_cls is None:
            f["deobf_but_decl_not_in_mapping"] += bool(got)
            continue
        f["app_fields"] += 1
        origs = fields.get((decl, fld))
        if not origs:
            f["not_in_mapping"] += 1
            continue
        exp = {o if "." in o else f"{orig_cls}.{o}" for o in origs}
        renamed = not (orig_cls == decl and fld in origs)
        key = "renamed" if renamed else "identity"
        f[key] += 1
        if got is None:
            f[f"{key}_missing"] += 1
            if renamed and len(ex["missing_field"]) < 10:
                ex["missing_field"].append({"field": name, "expected": sorted(exp)})
        elif got in exp:
            f[f"{key}_correct"] += 1
        else:
            f["wrong"] += 1
            len(ex["wrong_field"]) < 10 and ex["wrong_field"].append({"field": name, "got": got, "expected": sorted(exp)})

    res = {"trace": a.trace, "classes": dict(c), "fields": dict(f), "examples": ex}
    out = pathlib.Path(a.out or pathlib.Path(a.trace).with_name("verify_heap_dump.json"))
    out.write_text(json.dumps(res, indent=2))
    print("classes", json.dumps(res["classes"]))
    print("fields ", json.dumps(res["fields"]))
    for k, v in ex.items():
        for e in v[:3]:
            print(k.upper(), e)


if __name__ == "__main__":
    main()
