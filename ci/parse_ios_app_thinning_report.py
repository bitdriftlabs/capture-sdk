#!/usr/bin/env python3

import argparse
import re
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path


SIZE_RE = re.compile(
    r"^App size:\s+(?P<value>Zero|[\d][\d,\s]*(?:\.\d+)?)\s+"
    r"(?P<unit>[KMG]B)\s+compressed,",
    re.IGNORECASE,
)


def _blocks(report):
    blocks = []
    current = []
    for line in report.splitlines():
        if line.startswith("Variant: "):
            if current:
                blocks.append("\n".join(current))
            current = [line]
        elif current:
            current.append(line)
    if current:
        blocks.append("\n".join(current))
    return blocks


def _to_kb(value, unit):
    if value.lower() == "zero":
        return 0

    normalized = value.replace(",", "").replace(" ", "")
    size = Decimal(normalized)
    multiplier = {
        "KB": Decimal(1),
        "MB": Decimal(1024),
        "GB": Decimal(1024 * 1024),
    }[unit.upper()]
    return int((size * multiplier).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def parse_app_size_kb(report, device_model):
    matching_blocks = [block for block in _blocks(report) if device_model in block]
    if not matching_blocks:
        raise ValueError(f"no app thinning report variant found for {device_model}")

    for block in matching_blocks:
        for line in block.splitlines():
            match = SIZE_RE.match(line.strip())
            if match:
                return _to_kb(match.group("value"), match.group("unit"))

    raise ValueError(f"no compressed app size found for {device_model}")


def main():
    parser = argparse.ArgumentParser(
        description="Extract compressed iOS app size in KB from an App Thinning Size Report.",
    )
    parser.add_argument("report", type=Path)
    parser.add_argument("device_model")
    args = parser.parse_args()

    try:
        print(parse_app_size_kb(args.report.read_text(), args.device_model))
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
