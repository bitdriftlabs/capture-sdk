#!/usr/bin/env python3
# capture-sdk - bitdrift's client SDK
# Copyright Bitdrift, Inc. All rights reserved.
#
# Use of this source code is governed by a source available license that can be found in the
# LICENSE file or at:
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

"""
Parse AndroidX Benchmark JSON output and generate a markdown report.

Usage:
    python3 parse_benchmark_results.py <benchmark_json_path> [baseline_json_path]

Output:
    Prints a markdown-formatted table to stdout with benchmark results.
    If baseline is provided, includes comparison columns.
"""

import json
import re
import sys


def format_time_ns(ns):
    """Format nanoseconds into a human-readable string."""
    if ns >= 1_000_000_000:
        return f"{ns / 1_000_000_000:.2f} s"
    elif ns >= 1_000_000:
        return f"{ns / 1_000_000:.2f} ms"
    elif ns >= 1_000:
        return f"{ns / 1_000:.2f} us"
    else:
        return f"{ns:.0f} ns"


def format_allocations(count):
    """Format allocation count as exact integer."""
    return str(int(round(count)))


def format_diff_percent(current, baseline, current_formatted, baseline_formatted):
    """Format the difference as a percentage with indicator."""
    if baseline == 0:
        return "N/A"
    # If the formatted display values are the same, show 0%
    if current_formatted == baseline_formatted:
        return "0.0%"
    diff_percent = ((current - baseline) / baseline) * 100
    if diff_percent > 5:
        return f"+{diff_percent:.1f}%"
    elif diff_percent < -5:
        return f"{diff_percent:.1f}%"
    else:
        return f"{diff_percent:+.1f}%"


def clean_test_name(name):
    """Clean up test name by removing benchmark suffixes and prefixes."""
    # Remove suffixes like [EMULATOR_UNLOCKED], [UNLOCKED], [EMULATOR], etc.
    cleaned = re.sub(r"\[.*?\]$", "", name)
    # Remove EMULATOR_ prefix
    cleaned = re.sub(r"^EMULATOR_", "", cleaned)
    return cleaned.strip()


def extract_device_info(data):
    """Extract device information from the benchmark JSON context."""
    context = data.get("context", {})
    build = context.get("build", {})
    
    device_info = {
        "model": build.get("model", "Unknown"),
        "device": build.get("device", "Unknown"),
        "manufacturer": build.get("manufacturer", "Unknown"),
        "api_level": build.get("version", {}).get("sdk", "Unknown"),
        "fingerprint": build.get("fingerprint", ""),
    }
    
    # Detect if running on emulator based on common emulator fingerprints
    fingerprint = device_info["fingerprint"].lower()
    device_info["is_emulator"] = any(x in fingerprint for x in ["sdk_gphone", "emulator", "generic"])
    
    return device_info


def format_device_info(device_info):
    """Format device info into a readable string."""
    device_type = "Emulator" if device_info.get("is_emulator") else "Physical Device"
    model = device_info.get("model", "Unknown")
    api_level = device_info.get("api_level", "Unknown")
    
    return f"{device_type} - {model} (API {api_level})"


def parse_benchmark_json(json_path):
    """Parse the benchmark JSON file and extract metrics."""
    with open(json_path, "r") as f:
        data = json.load(f)

    benchmarks = data.get("benchmarks", [])
    results = {}

    for benchmark in benchmarks:
        name = clean_test_name(benchmark.get("name", "unknown"))
        metrics = benchmark.get("metrics", {})

        # Extract time metrics (timeNs)
        time_metrics = metrics.get("timeNs", {})
        time_median = time_metrics.get("median", 0)

        # Extract allocation metrics (allocationCount)
        alloc_metrics = metrics.get("allocationCount", {})
        alloc_median = alloc_metrics.get("median", 0)

        results[name] = {
            "time_median": time_median,
            "alloc_median": alloc_median,
        }

    # Extract device info from the JSON
    device_info = extract_device_info(data)

    return results, device_info


def generate_markdown_report(results, baseline=None, device_info=None, baseline_device_info=None):
    """Generate markdown tables from benchmark results."""
    if not results:
        return "No benchmark results found."

    lines = []

    # Add device information header
    if device_info:
        device_str = format_device_info(device_info)
        if baseline_device_info:
            baseline_device_str = format_device_info(baseline_device_info)
            lines.append(f"> **Device:** PR: {device_str} | main: {baseline_device_str}")
        else:
            lines.append(f"> **Device:** {device_str}")
    
        # Add note about emulator variance if running on emulator
        if device_info.get("is_emulator"):
            lines.append(">")
            lines.append("> **Note:** Benchmarks run on emulator. Results may vary between runs and may differ from physical devices.")
    lines.append("")

    # Allocations table
    lines.append("### Allocations")
    lines.append("")
    if baseline:
        lines.append("| Test | PR | main | Δ |")
        lines.append("|------|-----|------|---|")
    else:
        lines.append("| Test | Median |")
        lines.append("|------|--------|")

    for name, data in sorted(results.items()):
        # Round allocations to integers for comparison (eliminates noise from fractional differences)
        current_alloc = int(round(data["alloc_median"]))
        alloc_median = format_allocations(current_alloc)

        if baseline and name in baseline:
            baseline_alloc = int(round(baseline[name]["alloc_median"]))
            alloc_baseline_fmt = format_allocations(baseline_alloc)
            alloc_diff = format_diff_percent(current_alloc, baseline_alloc, alloc_median, alloc_baseline_fmt)
            lines.append(f"| {name} | {alloc_median} | {alloc_baseline_fmt} | {alloc_diff} |")
        elif baseline:
            lines.append(f"| {name} | {alloc_median} | N/A | N/A |")
        else:
            lines.append(f"| {name} | {alloc_median} |")

    # Timing table
    lines.append("")
    lines.append("### Timing")
    lines.append("")
    if baseline:
        lines.append("| Test | PR | main | Δ |")
        lines.append("|------|-----|------|---|")
    else:
        lines.append("| Test | Median |")
        lines.append("|------|--------|")

    for name, data in sorted(results.items()):
        time_median = format_time_ns(data["time_median"])

        if baseline and name in baseline:
            baseline_time = baseline[name]["time_median"]
            time_baseline_fmt = format_time_ns(baseline_time)
            time_diff = format_diff_percent(data["time_median"], baseline_time, time_median, time_baseline_fmt)
            lines.append(f"| {name} | {time_median} | {time_baseline_fmt} | {time_diff} |")
        elif baseline:
            lines.append(f"| {name} | {time_median} | N/A | N/A |")
        else:
            lines.append(f"| {name} | {time_median} |")

    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(
            "Usage: python3 parse_benchmark_results.py <benchmark_json_path> [baseline_json_path]",
            file=sys.stderr,
        )
        sys.exit(1)

    json_path = sys.argv[1]
    baseline_path = sys.argv[2] if len(sys.argv) > 2 else None

    try:
        results, device_info = parse_benchmark_json(json_path)
        baseline = None
        baseline_device_info = None
        if baseline_path:
            baseline, baseline_device_info = parse_benchmark_json(baseline_path)
        report = generate_markdown_report(results, baseline, device_info, baseline_device_info)
        print(report)
    except FileNotFoundError as e:
        print(f"Error: File not found: {e.filename}", file=sys.stderr)
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Invalid JSON: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
