#!/usr/bin/env python3

import argparse
import re
import sys
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

# Reduced example of the structure we parse from "App Thinning Size Report.txt":
#
# Variant: ExampleApp.ipa
# Supported variant descriptors: [device: iPhone18,3, os-version: 26.0]
# App + On Demand Resources size: 8.2 MB compressed, 21.6 MB uncompressed
# App size: 7.7 MB compressed, 20.1 MB uncompressed
# On Demand Resources size: Zero KB compressed, Zero KB uncompressed
#
# The parser splits the report into "Variant:" blocks and extracts the numeric
# value from the selected block's "App size:" line.
# - If the report has only one variant block, that block is used directly.
# - If the report has multiple variant blocks and all report the same app size,
#   that shared size is used directly.
# - If the report has multiple variant blocks with different sizes, the requested
#   device model is used to select the matching block.
SIZE_RE = re.compile(
    r"^App size:\s+(?P<value>Zero|[\d][\d,\s]*(?:\.\d+)?)\s+"
    r"(?P<unit>[KMG]B)\s+compressed,",
    re.IGNORECASE,
)


def _blocks(report):
    """Split the report into per-variant sections starting at each 'Variant:' line."""
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
    """Convert the compressed size from KB/MB/GB text into rounded KB."""
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


def _extract_block_size_kb(block):
    for line in block.splitlines():
        match = SIZE_RE.match(line.strip())
        if match:
            return _to_kb(match.group("value"), match.group("unit"))
    return None


def parse_app_size_kb(report, device_model=None):
    """Return the compressed app size from the selected app thinning report block."""
    blocks = _blocks(report)
    if len(blocks) == 1:
        matching_blocks = blocks
    else:
        block_sizes = [_extract_block_size_kb(block) for block in blocks]
        unique_sizes = {size for size in block_sizes if size is not None}
        if len(unique_sizes) == 1:
            return unique_sizes.pop()
        if device_model:
            matching_blocks = [block for block in blocks if device_model in block]
        else:
            raise ValueError("multiple app thinning report variants found; pass a device model")

    if device_model and len(blocks) > 1 and not matching_blocks:
        raise ValueError(f"no app thinning report variant found for {device_model}")

    if device_model and len(blocks) > 1:
        matching_blocks = [block for block in blocks if device_model in block]

    for block in matching_blocks:
        size_kb = _extract_block_size_kb(block)
        if size_kb is not None:
            return size_kb

    if device_model:
        raise ValueError(f"no compressed app size found for {device_model}")
    raise ValueError("no compressed app size found in the app thinning report")


def main():
    parser = argparse.ArgumentParser(
        description="Extract compressed iOS app size in KB from an App Thinning Size Report.",
    )
    parser.add_argument("report", type=Path)
    parser.add_argument("device_model", nargs="?")
    args = parser.parse_args()

    try:
        print(parse_app_size_kb(args.report.read_text(), args.device_model))
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
