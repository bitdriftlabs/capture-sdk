#!/usr/bin/env python3
"""Render the largest iOS static-archive contributors between two snapshots."""

import argparse
import json
import sys
from pathlib import Path


def load_snapshot(path):
    with path.open() as source:
        return json.load(source)


def format_kb(value):
    sign = "+" if value > 0 else ""
    return f"{sign}{value / 1024:.0f} KB"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("current", type=Path)
    parser.add_argument("--limit", type=int, default=5)
    parser.add_argument("--minimum-delta-bytes", type=int, default=4096)
    args = parser.parse_args()

    baseline = load_snapshot(args.baseline)
    current = load_snapshot(args.current)
    if baseline["schema_version"] != current["schema_version"]:
        return 0
    if baseline["toolchain"] != current["toolchain"]:
        return 0

    components = set(baseline["components"]) | set(current["components"])
    changes = []
    for component in components:
        before = baseline["components"].get(component, {})
        after = current["components"].get(component, {})
        archive_delta = after.get("archive_bytes", 0) - before.get("archive_bytes", 0)
        if archive_delta < args.minimum_delta_bytes:
            continue
        text_delta = after.get("text_bytes", 0) - before.get("text_bytes", 0)
        changes.append((archive_delta, text_delta, component))

    if not changes:
        return 0

    print("### SDK archive diagnostics")
    print()
    print("| Component | Archive delta | Executable code delta |")
    print("|---|---:|---:|")
    for archive_delta, text_delta, component in sorted(
        changes, key=lambda change: change[0], reverse=True
    )[: args.limit]:
        print(
            f"| `{component}` | {format_kb(archive_delta)} | {format_kb(text_delta)} |"
        )
    print()
    print("> Diagnostic only: thinned app size is the actual impact in an app.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
