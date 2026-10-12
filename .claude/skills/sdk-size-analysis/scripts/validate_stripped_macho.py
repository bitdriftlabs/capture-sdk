"""Validate unsigned thin ARM64 Mach-O stripping without changing either input."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

from macholib import mach_o
from macholib.MachO import MachO


def require(condition, message):
    if not condition:
        raise ValueError(message)


def export_entries(data):
    def read_uleb(offset):
        value = 0
        shift = 0
        while True:
            require(offset < len(data) and shift < 64, "invalid export trie integer")
            byte = data[offset]
            offset += 1
            value |= (byte & 0x7f) << shift
            if not byte & 0x80:
                return value, offset
            shift += 7

    entries = {}

    def visit(offset, prefix, ancestors):
        require(offset not in ancestors, "cyclic export trie")
        terminal_size, terminal_start = read_uleb(offset)
        terminal_end = terminal_start + terminal_size
        require(terminal_end < len(data), "export trie terminal out of bounds")
        if terminal_size:
            entries[prefix] = data[terminal_start:terminal_end]
        child_count = data[terminal_end]
        cursor = terminal_end + 1
        for child_index in range(child_count):
            name_end = data.index(b"\0", cursor)
            edge = data[cursor:name_end].decode()
            child_offset, cursor = read_uleb(name_end + 1)
            visit(child_offset, prefix + edge, ancestors | {offset})

    if data:
        visit(0, "", set())
    return entries


def inspect(path):
    data = path.read_bytes()
    macho = MachO(str(path))
    require(len(macho.headers) == 1 and data[:4] == b"\xcf\xfa\xed\xfe",
            "expected thin little-endian 64-bit Mach-O")
    require(macho.headers[0].header.cputype == 0x0100000c, "expected ARM64")
    sections = {}
    payloads = {}
    commands = {}
    undefined = []
    symbols = []
    exports = {}
    symtab = None
    indirect = None
    for load, command, payload in macho.headers[0].commands:
        name = mach_o.LC_NAMES.get(load.cmd, str(load.cmd))
        if load.cmd == mach_o.LC_SEGMENT_64:
            for section in payload:
                key = (section.segname.rstrip(b"\0").decode(), section.sectname.rstrip(b"\0").decode())
                zero_fill = int(section.flags) & 0xff in (1, 12, 18)
                body = data[section.offset:section.offset + section.size] if not zero_fill else b""
                require(zero_fill or len(body) == section.size, "section out of bounds")
                sections[key] = (int(section.addr), int(section.size), int(section.flags),
                                 None if zero_fill else hashlib.sha256(body).hexdigest())
        elif name == "LC_SYMTAB":
            symtab = command
        elif name == "LC_DYSYMTAB":
            indirect = command
        elif name == "LC_DYLD_EXPORTS_TRIE":
            exports = export_entries(data[command.dataoff:command.dataoff + command.datasize])
        elif name == "LC_CODE_SIGNATURE":
            require(not command.datasize, "signed binaries require a separate resigning audit")
        elif name in ("LC_DYLD_CHAINED_FIXUPS", "LC_FUNCTION_STARTS", "LC_DATA_IN_CODE"):
            payloads[name] = data[command.dataoff:command.dataoff + command.datasize]
        elif name in ("LC_DYLD_INFO", "LC_DYLD_INFO_ONLY"):
            for field in ("rebase", "bind", "weak_bind", "lazy_bind", "export"):
                offset = getattr(command, f"{field}_off")
                size = getattr(command, f"{field}_size")
                body = data[offset:offset + size]
                if field == "export":
                    exports.update(export_entries(body))
                else:
                    payloads[(name, field)] = body
        elif name in (
            "LC_LOAD_DYLIB", "LC_LOAD_WEAK_DYLIB", "LC_REEXPORT_DYLIB", "LC_LOAD_UPWARD_DYLIB",
            "LC_RPATH", "LC_MAIN", "LC_UUID", "LC_BUILD_VERSION", "LC_SOURCE_VERSION",
        ):
            commands.setdefault(name, []).append(command.to_str() + payload)
    require(symtab is not None and indirect is not None, "missing symbol/dynamic-symbol table")
    strings = data[symtab.stroff:symtab.stroff + symtab.strsize]
    for index in range(symtab.nsyms):
        string_index, symbol_type, section_index, description, value = struct.unpack_from(
            "<IBBHQ", data, symtab.symoff + index * 16)
        symbol_name = strings[string_index:].split(b"\0", 1)[0].decode(errors="replace")
        symbol = (symbol_name, symbol_type, section_index, description, value)
        symbols.append(symbol)
        if symbol_type & 0x0e == 0:
            undefined.append(symbol)
    indirect_symbols = []
    for index in range(indirect.nindirectsyms):
        symbol_index = struct.unpack_from("<I", data, indirect.indirectsymoff + index * 4)[0]
        indirect_symbols.append(symbol_index if symbol_index & 0xc0000000 else symbols[symbol_index])
    return dict(file_bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), sections=sections,
                loader_payloads=payloads, loader_commands=commands, undefined=undefined,
                symbols=symbols, indirect_symbols=indirect_symbols, exports=exports)


def validate(original, stripped):
    before = inspect(original)
    after = inspect(stripped)
    for key in ("sections", "loader_payloads", "loader_commands", "undefined", "indirect_symbols"):
        require(before[key] == after[key], f"stripping changed {key}")
    require(after["exports"].items() <= before["exports"].items(), "surviving export targets changed")
    for symbol in before["symbols"]:
        if symbol[1] & 1 and symbol[3] & 0x10:
            require(symbol in after["symbols"], "dynamically referenced symbol removed")
            if symbol[0] in before["exports"]:
                require(symbol[0] in after["exports"], "dynamic export removed")
    before_names = {symbol[0] for symbol in before["symbols"]}
    after_names = {symbol[0] for symbol in after["symbols"]}
    require(after_names - before_names <= {"radr://5614542"}, "unexpected new stripped symbols")
    require(not any(name.startswith("__R") or re.search(r"17h[0-9a-f]{16}E(?:\.llvm\..*)?$", name)
                    for name in after_names), "ordinary Rust symbols remain")
    return dict(original_bytes=before["file_bytes"], stripped_bytes=after["file_bytes"],
                original_sha256=before["sha256"], stripped_sha256=after["sha256"],
                sections_preserved=len(after["sections"]), undefined_preserved=len(after["undefined"]),
                indirect_targets_preserved=len(after["indirect_symbols"]),
                symbols_before=len(before["symbols"]), symbols_after=len(after["symbols"]),
                exports_before=len(before["exports"]), exports_after=len(after["exports"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("stripped", type=Path)
    args = parser.parse_args()
    print(json.dumps(validate(args.original, args.stripped), indent=2))


if __name__ == "__main__":
    main()
