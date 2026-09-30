#!/usr/bin/env python3
"""Create a compact, comparable size snapshot for the iOS static archive."""

import argparse
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

RUST_MEMBER_RE = re.compile(r"^(?P<crate>.+)-\d+\.[^.]+\..*\.o$")
OBJECT_HEADER_RE = re.compile(r"\((?P<member>[^()]*)\):$")
SECTION_RE = re.compile(
    r"^\s*Section \((?P<segment>[^,]+),\s*(?P<section>[^)]+)\):\s*(?P<size>\d+)$"
)


def run(*args):
    return subprocess.check_output(args, text=True)


def component_name(member):
    if member == "__.SYMDEF":
        return "archive_symbol_index"
    match = RUST_MEMBER_RE.match(member)
    return match.group("crate") if match else member


def archive_members(archive):
    components = defaultdict(
        lambda: {"archive_bytes": 0, "text_bytes": 0, "data_bytes": 0}
    )
    for line in run("xcrun", "ar", "-tv", str(archive)).splitlines():
        # `ar -tv` prints one archive member per line. Its third field is the size and its final field is the member name
        fields = line.split()
        if len(fields) < 3:
            continue
        try:
            size = int(fields[2])
        except ValueError:
            continue

        components[component_name(fields[-1])]["archive_bytes"] += size
    return components


def section_sizes(archive, components):
    current_component = None
    for line in run("xcrun", "size", "-m", str(archive)).splitlines():
        header = OBJECT_HEADER_RE.search(line)
        if header:
            current_component = component_name(header.group("member"))
            continue

        # Ignore object totals and unrelated tool output; only individual sections contribute to the code/data breakdown.
        section = SECTION_RE.match(line)
        if not section or current_component is None:
            continue

        size = int(section.group("size"))
        component = components[current_component]
        if (section.group("segment"), section.group("section")) == ("__TEXT", "__text"):
            # __TEXT,__text is actual code. Everything else is reported as data/metadata for this diagnostic.
            component["text_bytes"] += size
        else:
            component["data_bytes"] += size


def xcode_version():
    for line in run("xcodebuild", "-version").splitlines():
        if line.startswith("Xcode "):
            return line.removeprefix("Xcode ")
    return "unknown"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--architecture", default="ios-arm64")
    args = parser.parse_args()

    if not args.archive.is_file():
        parser.error(f"archive does not exist: {args.archive}")

    components = archive_members(args.archive)
    section_sizes(args.archive, components)

    snapshot = {
        "schema_version": 1,
        "toolchain": {"xcode": xcode_version(), "architecture": args.architecture},
        "archive_bytes": args.archive.stat().st_size,
        "components": dict(sorted(components.items())),
    }
    print(json.dumps(snapshot, separators=(",", ":"), sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(error, file=sys.stderr)
        sys.exit(error.returncode)
