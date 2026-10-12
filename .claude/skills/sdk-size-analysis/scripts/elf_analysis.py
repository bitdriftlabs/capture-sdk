"""Audit an Android ELF and produce baseline-neutral code/file accounting (Python 3.11+)."""

import argparse
from bisect import bisect_right
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import tomllib
import zipfile

from elftools.elf.elffile import ELFFile

from categories import CRATE_GROUPS, PROTO_REFLECTION, classify


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inspect_elf(path):
    with path.open("rb") as source:
        elf = ELFFile(source)
        sections = []
        build_ids = []
        for section in elf.iter_sections():
            if section["sh_type"] == "SHT_NOTE":
                build_ids.extend(note["n_desc"] for note in section.iter_notes()
                                 if note["n_type"] == "NT_GNU_BUILD_ID")
            backed = section["sh_type"] not in {"SHT_NOBITS", "SHT_NULL"}
            sections.append(dict(
                name=section.name, address=section["sh_addr"], size=section["sh_size"],
                offset=section["sh_offset"], kind=section["sh_type"], flags=section["sh_flags"],
                sha256=hashlib.sha256(section.data()).hexdigest() if backed else None,
            ))
        return dict(sections=sections, build_ids=build_ids, machine=elf["e_machine"],
                    bits=elf.elfclass, little_endian=elf.little_endian)


def allocated(sections):
    return {section["name"]: {key: section[key] for key in
            ("address", "size", "kind", "flags", "sha256")}
            for section in sections if section["flags"] & 2}


def nm_symbols(tool, path, demangle):
    output = subprocess.check_output([
        str(tool), "--defined-only", "--no-sort", "--print-size",
        "--demangle" if demangle else "--no-demangle", str(path),
    ], text=True)
    result = []
    for line in output.splitlines():
        match = re.match(r"^\s*([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\s+(\S)\s+(.+)$", line)
        if match:
            result.append(dict(address=int(match[1], 16), size=int(match[2], 16),
                               kind=match[3], name=match[4]))
    return result


def symbol_identity(symbols):
    return sorted((entry["address"], entry["size"], entry["name"]) for entry in symbols)


def verify_debug_symbols(original, uploaded):
    require(symbol_identity(original) == symbol_identity(uploaded), "uploaded symbol identities differ")
    ordered_original = sorted(original, key=lambda entry: (entry["address"], entry["size"], entry["name"]))
    ordered_uploaded = sorted(uploaded, key=lambda entry: (entry["address"], entry["size"], entry["name"]))
    for before, after in zip(ordered_original, ordered_uploaded):
        require(before["kind"] == after["kind"] or
                (before["kind"], after["kind"]) in {("d", "b"), ("r", "b"), ("D", "B"), ("R", "B")},
                "unexpected uploaded debug symbol kind change")


def partition_text(symbols, start, size):
    stop = start + size
    events = defaultdict(lambda: {"add": [], "remove": []})
    events[start]
    events[stop]
    for index, entry in enumerate(symbols):
        if entry["kind"].lower() != "t" or not entry["size"]:
            continue
        left = max(start, entry["address"])
        right = min(stop, entry["address"] + entry["size"])
        if left < right:
            events[left]["add"].append(index)
            events[right]["remove"].append(index)
    active = set()
    boundaries = sorted(events)
    result = []
    for index, left in enumerate(boundaries[:-1]):
        active.difference_update(events[left]["remove"])
        active.update(events[left]["add"])
        names = sorted({symbols[identity]["name"] for identity in active})
        owners = {classify(name) for name in names}
        if not owners:
            category, origin = "Unattributed text", "gap"
        elif len(owners) == 1:
            category, origin = next(iter(owners))
        else:
            categories = {owner[0] for owner in owners}
            category = next(iter(categories)) if len(categories) == 1 else "Mixed category aliases"
            origin = "mixed aliases"
        result.append(dict(address=left, size=boundaries[index + 1] - left,
                           category=category, origin=origin, names=names,
                           symbol_count=len(active)))
    require(sum(entry["size"] for entry in result) == size, "text partition does not reconcile")
    return result


def label_debug_disassembly(output, hotspot, partition):
    addresses = [entry["address"] for entry in partition]

    def target_label(match):
        address = int(match[1], 16)
        index = bisect_right(addresses, address) - 1
        if index >= 0:
            entry = partition[index]
            if address < entry["address"] + entry["size"] and entry["names"]:
                name = "; ".join(entry["names"])
                offset = address - entry["address"]
                return f"{match[1]} <{name}" + (f"+0x{offset:x}>" if offset else ">")
        return f"{match[1]} <unresolved>"

    output = re.sub(r"(?m)^[0-9a-f]+ <[^\n]+>:\n", "", output)
    output = re.sub(r"(0x[0-9a-f]+)\s+<[^>\n]+>", target_label, output)
    name = "; ".join(hotspot["names"])
    return (f"Hotspot: {name}\nRange: 0x{hotspot['address']:x}.."
            f"0x{hotspot['address'] + hotspot['size']:x}\n"
            "Labels restored from audited debug symbols; unresolved targets remain explicit.\n\n" + output)


def logger_analysis(partition, manifest):
    category = CRATE_GROUPS["bd_logger"]
    bucket = [entry for entry in partition if entry["category"] == category]
    logger = [entry for entry in bucket if entry["origin"] == "bd_logger"]
    modules = Counter()
    shapes = Counter()
    for entry in logger:
        name = entry["names"][0]
        match = re.search(r"\bbd_logger::([a-zA-Z0-9_]+)", name)
        modules[match[1] if match else "unknown"] += entry["size"]
        if "drop_in_place" in name:
            shape = "Drop glue"
        elif "fmt::" in name or "::fmt" in name:
            shape = "Formatting helpers"
        elif name.startswith(("bd_logger::", "<bd_logger::")):
            shape = "Logger functions and async bodies"
        else:
            shape = "Generic typed helpers and runtime wrappers"
        shapes[shape] += entry["size"]
    dependencies = set()
    if manifest:
        document = tomllib.loads(manifest.read_text())
        tables = [document.get("dependencies", {})]
        tables.extend(target.get("dependencies", {}) for target in document.get("target", {}).values())
        for table in tables:
            for name, value in table.items():
                dependencies.add((value.get("package", name) if isinstance(value, dict) else name)
                                 .replace("-", "_"))
    named_dependencies = Counter()
    for entry in partition:
        if entry["category"] != category and entry["origin"] in dependencies:
            named_dependencies[entry["origin"]] += entry["size"]
    return dict(bucket_bytes=sum(entry["size"] for entry in bucket),
                logger_bytes=sum(modules.values()), modules=dict(modules.most_common()),
                bucket_origins=dict(sum_counters(bucket, "origin")),
                shapes=dict(shapes.most_common()),
                direct_dependency_named_bytes=dict(named_dependencies.most_common()),
                largest=sorted(logger, key=lambda entry: -entry["size"])[:8])


def sum_counters(entries, key):
    counter = Counter()
    for entry in entries:
        counter[entry[key]] += entry["size"]
    return counter.most_common()


def markdown(report):
    lines = [f"# {report['label']}", "", f"SDK revision: `{report['revision']}`.",
             f"Build ID: `{', '.join(report['build_ids'])}`.",
             f"Native SHA-256: `{report['native_sha256']}`.", "",
             f"Raw file: {report['raw_bytes']:,} B; ZIP: {report['zip_bytes']} B; "
             f"executable: {report['executable_bytes']:,} B; `.text`: {report['text_bytes']:,} B.",
             "", "Whole-function labels include generic/inlined support, not removable feature costs.",
             "", "| Category | Code Bytes | % Executable |", "| --- | ---: | ---: |"]
    for category, size in report["categories"].items():
        lines.append(f"| {category} | {size:,} | {100 * size / report['executable_bytes']:.2f}% |")
    lines.extend(["", "| File-Backed Section | Bytes |", "| --- | ---: |"])
    for section in report["raw_sections"]:
        lines.append(f"| {section['name']} | {section['size']:,} |")
    lines.append(f"| Headers and padding | {report['headers_padding_bytes']:,} |")
    logger = report["logger"]
    lines.extend(["", "## Logger", "", f"Logger label: {logger['logger_bytes']:,} B.",
                  "", "| Module | Bytes |", "| --- | ---: |"])
    for module, size in logger["modules"].items():
        lines.append(f"| {module} | {size:,} |")
    lines.extend(["", "| Largest Logger Function | Bytes |", "| --- | ---: |"])
    for entry in logger["largest"]:
        lines.append(f"| `{entry['names'][0].replace('|', '&#124;')}` | {entry['size']:,} |")
    lines.extend(["", "Named direct dependencies are outside the logger bucket; do not add them",
                  "to every caller or multiply callee size by static call-site counts.", "",
                  "| Direct Dependency Label | Bytes |", "| --- | ---: |"])
    for crate, size in logger["direct_dependency_named_bytes"].items():
        lines.append(f"| {crate} | {size:,} |")
    return "\n".join(lines) + "\n"


def analyze(args):
    args.output.mkdir(parents=True, exist_ok=True)
    shipped = inspect_elf(args.binary)
    source = inspect_elf(args.symbols_elf)
    require(shipped["build_ids"] and shipped["build_ids"] == source["build_ids"],
            "missing/mismatched shipped and symbol ELF build IDs")
    for key in ("machine", "bits", "little_endian"):
        require(shipped[key] == source[key], f"ELF {key} differs")
    if args.symbols_mode == "full":
        require(allocated(shipped["sections"]) == allocated(source["sections"]),
                "full/shipped allocated sections differ")
    else:
        original_sections = allocated(shipped["sections"])
        debug_sections = allocated(source["sections"])
        require(original_sections.keys() == debug_sections.keys(), "debug section set differs")
        for name, original in original_sections.items():
            debug = debug_sections[name]
            require(all(original[key] == debug[key] for key in ("address", "size", "flags")),
                    f"debug section layout differs: {name}")
            require(debug["kind"] == "SHT_NOBITS" or original == debug,
                    f"debug retained section content differs: {name}")
    nm = args.tools / "llvm-nm"
    raw = nm_symbols(nm, args.symbols_elf, False)
    decoded = nm_symbols(nm, args.symbols_elf, True)
    require(len(raw) == len(decoded), "raw/demangled symbol count differs")
    for original, entry in zip(raw, decoded):
        require(all(original[key] == entry[key] for key in ("address", "size", "kind")),
                "raw/demangled symbol order differs")
        entry["raw_name"] = original["name"]
    if args.debug_map:
        with tempfile.TemporaryDirectory(dir=args.output) as temporary:
            debug_path = Path(temporary) / "uploaded.elf"
            debug_path.write_bytes(gzip.decompress(args.debug_map.read_bytes()))
            require(inspect_elf(debug_path)["build_ids"] == shipped["build_ids"],
                    "uploaded debug-map build ID differs")
            verify_debug_symbols(raw, nm_symbols(nm, debug_path, False))
    native = args.binary.read_bytes()
    entry_name = f"jni/{args.abi}/libcapture.so"
    for artifact in (args.aar, args.zip):
        if artifact:
            with zipfile.ZipFile(artifact) as archive:
                require(archive.read(entry_name) == native, f"native archive identity differs: {artifact}")
                require(archive.testzip() is None, f"archive CRC failure: {artifact}")
                if artifact == args.zip:
                    require(archive.namelist() == [entry_name], "native ZIP is not single-entry")
    text = next(section for section in shipped["sections"] if section["name"] == ".text")
    partition = partition_text(decoded, text["address"], text["size"])
    totals = Counter(dict(sum_counters(partition, "category")))
    executable = sum(section["size"] for section in shipped["sections"] if section["flags"] & 4)
    totals["Linker stubs/other executable sections"] += executable - text["size"]
    require(sum(totals.values()) == executable, "executable accounting differs")
    backed = sorted((section for section in shipped["sections"] if section["sha256"] is not None
                     and section["size"]), key=lambda section: section["offset"])
    cursor = 0
    for section in backed:
        require(cursor <= section["offset"], "overlapping file-backed ELF sections")
        cursor = section["offset"] + section["size"]
        require(cursor <= len(native), "section extends past file")
    residual = len(native) - sum(section["size"] for section in backed)
    require(residual >= 0, "negative file accounting residual")
    report = dict(
        schema_version=1, label=args.label, revision=args.revision, abi=args.abi,
        native_sha256=sha256(args.binary), build_ids=shipped["build_ids"],
        raw_bytes=len(native), zip_bytes=args.zip.stat().st_size if args.zip else None,
        executable_bytes=executable, text_bytes=text["size"],
        defined_symbols=len(decoded), code_symbols=sum(entry["kind"].lower() == "t" and
            entry["size"] > 0 for entry in decoded),
        categories=dict(totals.most_common()), raw_sections=sorted(backed, key=lambda section: -section["size"]),
        headers_padding_bytes=residual, zero_fill_bytes=sum(section["size"]
            for section in shipped["sections"] if section["kind"] == "SHT_NOBITS"),
        dwarf_sections=[section["name"] for section in source["sections"]
                        if section["name"].startswith(".debug_")],
        text_gaps_bytes=sum(entry["size"] for entry in partition if not entry["names"]),
        text_multiple_symbols_bytes=sum(entry["size"] for entry in partition if entry["symbol_count"] > 1),
        audit=dict(symbols_mode=args.symbols_mode, allocated_content_verified=args.symbols_mode == "full",
                   debug_map_verified=bool(args.debug_map), aar_verified=bool(args.aar),
                   zip_verified=bool(args.zip)),
        artifacts={name: dict(path=str(path), sha256=sha256(path)) for name, path in
                   (("binary", args.binary), ("symbols_elf", args.symbols_elf),
                    ("debug_map", args.debug_map), ("aar", args.aar), ("zip", args.zip)) if path},
        logger=logger_analysis(partition, args.logger_manifest),
        largest_functions=sorted(partition, key=lambda entry: -entry["size"])[:30],
        descriptor_reflection_symbols=[entry for entry in decoded if PROTO_REFLECTION.search(entry["name"])],
    )
    proto_shapes = Counter()
    for entry in partition:
        if entry["category"] == "Protobuf messages and type-specialized support":
            name = entry["names"][0]
            if name.startswith(("bd_proto::protos::", "<bd_proto::protos::")):
                shape = "Generated merge/size/write methods" if re.search(
                    r">::(merge_from|compute_size|write_to_with_cached_sizes)(?:$|::)", name) else \
                    "Other generated message functions"
            else:
                shape = "Protobuf-type-specialized container and support functions"
            proto_shapes[shape] += entry["size"]
    report["protobuf_message_shapes"] = dict(proto_shapes.most_common())
    by_address = {entry["address"]: entry for entry in partition}
    for index, entry in enumerate(report["logger"]["largest"]):
        output = subprocess.check_output([
            str(args.tools / "llvm-objdump"), "--disassemble", "--demangle",
            f"--start-address=0x{entry['address']:x}",
            f"--stop-address=0x{entry['address'] + entry['size']:x}",
            str(args.symbols_elf if args.symbols_mode == "full" else args.binary),
        ], text=True)
        instruction_addresses = [int(address, 16) for address in
                                 re.findall(r"^\s*([0-9a-f]+):\s", output, re.MULTILINE)]
        require(instruction_addresses and instruction_addresses[0] == entry["address"] and
                instruction_addresses[-1] < entry["address"] + entry["size"],
                "hotspot disassembly is missing or outside the requested symbol range")
        if args.symbols_mode == "debug":
            output = label_debug_disassembly(output, entry, partition)
        filename = f"logger-disassembly-{index:02d}.txt"
        (args.output / filename).write_text(output)
        targets = Counter(int(address, 16) for address in re.findall(r"\bbl\s+0x([0-9a-f]+)", output))
        entry["disassembly"] = filename
        entry["direct_calls"] = [dict(address=address, call_sites=count,
            names=by_address[address]["names"] if address in by_address else [],
            callee_bytes=by_address[address]["size"] if address in by_address else None)
            for address, count in targets.most_common()]
    (args.output / "analysis.json").write_text(json.dumps(report, indent=2) + "\n")
    (args.output / "symbols.json").write_text(json.dumps(decoded, indent=2) + "\n")
    (args.output / "partition.json").write_text(json.dumps(partition, indent=2) + "\n")
    (args.output / "ANALYSIS.md").write_text(markdown(report))
    if args.evidence_output:
        require(args.provenance is not None, "compact evidence requires --provenance")
        provenance = json.loads(args.provenance.read_text())
        require(provenance["sdk_revision"] == args.revision, "provenance SDK revision differs")
        require(provenance["artifacts"]["native"]["sha256"] == report["native_sha256"],
                "provenance native hash differs")
        evidence = {key: value for key, value in report.items() if key not in
                    {"artifacts", "largest_functions", "descriptor_reflection_symbols", "logger", "raw_sections"}}
        evidence["artifacts"] = {key: {"sha256": value["sha256"]} for key, value in report["artifacts"].items()}
        evidence["raw_sections"] = [{"name": section["name"], "bytes": section["size"]}
                                    for section in report["raw_sections"]]
        evidence["descriptor_reflection_symbol_count"] = len(report["descriptor_reflection_symbols"])
        evidence["provenance"] = {key: value for key, value in provenance.items() if key not in
                                  {"package_identities", "artifacts"}}
        evidence["logger"] = {key: value for key, value in report["logger"].items() if key != "largest"}
        evidence["logger"]["largest"] = [{"bytes": entry["size"], "names": entry["names"]}
                                         for entry in report["logger"]["largest"]]
        evidence["logger_manifest_sha256"] = sha256(args.logger_manifest) if args.logger_manifest else None
        args.evidence_output.parent.mkdir(parents=True, exist_ok=True)
        args.evidence_output.write_text(json.dumps(evidence, indent=2) + "\n")
    print(f"{args.label}: raw={report['raw_bytes']:,}, ZIP={report['zip_bytes']}, "
          f"text={report['text_bytes']:,}, executable={report['executable_bytes']:,}")
    print(f"Audit PASS; text gaps={report['text_gaps_bytes']}, "
          f"multiple-symbol bytes={report['text_multiple_symbols_bytes']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--symbols-elf", type=Path, required=True)
    parser.add_argument("--symbols-mode", choices=("full", "debug"), default="full")
    parser.add_argument("--debug-map", type=Path)
    parser.add_argument("--aar", type=Path)
    parser.add_argument("--zip", type=Path)
    parser.add_argument("--abi", default="arm64-v8a")
    parser.add_argument("--tools", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--logger-manifest", type=Path)
    parser.add_argument("--provenance", type=Path)
    parser.add_argument("--evidence-output", type=Path)
    analyze(parser.parse_args())


if __name__ == "__main__":
    main()
