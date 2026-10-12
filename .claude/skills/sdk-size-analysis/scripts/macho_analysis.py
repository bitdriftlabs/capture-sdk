"""Attribute a thin 64-bit linked Mach-O by sections, metadata and estimated symbol ranges."""

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess

from macholib import mach_o
from macholib.MachO import MachO

from categories import classify


def classify_macho(name, raw_name):
    if re.match(r"^_?\$[sS]|^__?T", raw_name):
        return "Swift functions and type-specialized support", "Swift"
    if re.match(r"^[+-]\[", name):
        return "Objective-C methods", "Objective-C"
    category, origin = classify(name)
    if category == "Android/JNI bridge":
        category = "Platform bridge"
    return category, origin


def nm_symbols(tool, path, demangle):
    output = subprocess.check_output([
        str(tool), "--defined-only", "--no-sort",
        "--demangle" if demangle else "--no-demangle", str(path),
    ], text=True)
    result = []
    for line in output.splitlines():
        match = re.match(r"^\s*([0-9a-fA-F]+)\s+(\S)\s+(.+)$", line)
        if match:
            result.append((int(match[1], 16), match[2], match[3]))
    return result


def analyze(path, nm):
    macho = MachO(str(path))
    if len(macho.headers) != 1 or path.read_bytes()[:4] != b"\xcf\xfa\xed\xfe":
        raise ValueError("expected thin little-endian 64-bit Mach-O; use lipo on a copy first")
    sections = {}
    segments = {}
    metadata = {}
    for load, command, payload in macho.headers[0].commands:
        if load.cmd == mach_o.LC_SEGMENT_64:
            segment = command.segname.rstrip(b"\0").decode()
            segments[segment] = dict(file_bytes=int(command.filesize), virtual_bytes=int(command.vmsize))
            for section in payload:
                name = section.sectname.rstrip(b"\0").decode()
                section_segment = section.segname.rstrip(b"\0").decode()
                zero_fill = int(section.flags) & 0xff in (1, 12, 18)
                sections[f"{section_segment},{name}"] = dict(
                    bytes=int(section.size), address=int(section.addr),
                    file_backed=not zero_fill, relocations=int(section.nreloc))
        elif load.cmd == mach_o.LC_SYMTAB:
            metadata.update(symbol_records=int(command.nsyms) * 16,
                            symbol_strings=int(command.strsize), symbol_count=int(command.nsyms))
        elif load.cmd == mach_o.LC_DYSYMTAB:
            metadata["indirect_symbols"] = int(command.nindirectsyms) * 4
        elif hasattr(command, "datasize"):
            metadata[mach_o.LC_NAMES.get(load.cmd, str(load.cmd))] = int(command.datasize)
        elif load.cmd in (mach_o.LC_DYLD_INFO, mach_o.LC_DYLD_INFO_ONLY):
            for name in ("rebase", "bind", "weak_bind", "lazy_bind", "export"):
                metadata[name] = int(getattr(command, f"{name}_size"))
    raw = nm_symbols(nm, path, False)
    decoded = nm_symbols(nm, path, True)
    if len(raw) != len(decoded) or any(before[:2] != after[:2] for before, after in zip(raw, decoded)):
        raise ValueError("raw/demangled symbol identities differ")
    text = sections["__TEXT,__text"]
    start = text["address"]
    stop = start + text["bytes"]
    by_address = defaultdict(list)
    name_bytes = Counter()
    for original, symbol in zip(raw, decoded):
        category, origin = classify_macho(symbol[2], original[2])
        name_bytes[category] += len(original[2].encode()) + 1
        if symbol[1].lower() == "t" and start <= symbol[0] < stop:
            by_address[symbol[0]].append(dict(name=symbol[2], category=category, origin=origin))
    addresses = sorted(by_address)
    totals = Counter()
    functions = []
    for index, address in enumerate(addresses):
        next_address = addresses[index + 1] if index + 1 < len(addresses) else stop
        aliases = by_address[address]
        owners = {entry["category"] for entry in aliases}
        category = next(iter(owners)) if len(owners) == 1 else "Mixed category aliases"
        size = next_address - address
        totals[category] += size
        functions.append(dict(address=address, estimated_bytes=size, category=category,
                              names=[entry["name"] for entry in aliases]))
    leading = addresses[0] - start if addresses else text["bytes"]
    if sum(totals.values()) + leading != text["bytes"]:
        raise ValueError("Mach-O text estimates do not reconcile")
    return dict(schema_version=1, file_bytes=path.stat().st_size,
                sha256=hashlib.sha256(path.read_bytes()).hexdigest(), sections=sections,
                segments=segments, metadata=metadata, text_categories=dict(totals.most_common()),
                defined_symbol_name_bytes=dict(name_bytes.most_common()),
                unattributed_leading_text=leading,
                limitations="Next-address estimates include alignment and anonymous trailing code; "
                            "metadata fields overlap segment sizes. Not exact LTO crate costs.",
                functions=sorted(functions, key=lambda entry: -entry["estimated_bytes"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--nm", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = analyze(args.binary, args.nm)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Mach-O accounting PASS: {report['file_bytes']:,} B; "
          f"text={report['sections']['__TEXT,__text']['bytes']:,} B")


if __name__ == "__main__":
    main()
