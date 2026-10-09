"""Build and audit a pristine standalone SDK; keep logs and artifacts under .tmp/."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
import zipfile

from collect_android import digest


SCRIPTS = Path(__file__).resolve().parent


def require(condition, message):
    if not condition:
        raise ValueError(message)


def unique(values, description):
    values = sorted(set(values))
    require(len(values) == 1, f"expected one {description}, got {values}")
    return values[0]


def action_paths(document):
    fragments = {entry["id"]: entry for entry in document["pathFragments"]}

    def path(identity):
        entry = fragments[identity]
        parent = entry.get("parentId")
        return f"{path(parent)}/{entry['label']}" if parent else entry["label"]

    return {entry["id"]: path(entry["pathFragmentId"]) for entry in document["artifacts"]}


def android_inputs(document, abi):
    paths = action_paths(document)
    sets = {entry["id"]: entry for entry in document["depSetOfFiles"]}

    def inputs(identity):
        entry = sets[identity]
        result = set(entry.get("directArtifactIds", []))
        for child in entry.get("transitiveDepSetIds", []):
            result.update(inputs(child))
        return result

    actions = [action for action in document["actions"] if any(
        paths[identity].endswith(f"/{abi}.debug.gz") for identity in action.get("outputIds", []))]
    require(len(actions) == 1, f"expected one debug-map action for {abi}")
    action = actions[0]
    dependencies = {paths[identity] for group in action.get("inputDepSetIds", [])
                    for identity in inputs(group)}
    return dict(
        debug_map=unique([paths[identity] for identity in action["outputIds"]
                          if paths[identity].endswith(".debug.gz")], "debug map"),
        full_elf=unique([path for path in dependencies if path.endswith("/libcapture.so")], "full ELF"),
        nm=unique([path for path in dependencies if path.endswith("/bin/llvm-nm")], "NDK llvm-nm"),
    )


def unsigned_build(original):
    start = original.index('ios_application(\n    name = "ios_app_size",')
    stop = original.index('\nxcarchive(', start)
    block = original[start:stop]
    block, count = re.subn(r'provisioning_profile = select\(\{.*?\}\),',
                          'provisioning_profile = ":unsigned_size_profile",', block, flags=re.S)
    require(count == 1, "ios_app_size profile declaration changed; refusing to edit BUILD")
    generator = ('genrule(\n    name = "unsigned_size_profile",\n'
                 '    outs = ["unsigned-size.mobileprovision"],\n    cmd = "touch $@",\n)\n\n')
    return original[:start] + generator + block + original[stop:]


@contextmanager
def unsigned_probe(build, backup):
    original = build.read_bytes()
    backup.write_bytes(original)
    try:
        build.write_text(unsigned_build(original.decode()))
        yield
    finally:
        build.write_bytes(original)


def verify_artifact(path, expected):
    require(path.is_file() and digest(path) == expected, f"artifact hash mismatch: {path}")


def load_run_provenance(output):
    combined = output / "run-provenance.all.json"
    if combined.exists():
        return json.loads(combined.read_text())
    paths = [output / f"run-provenance.{platform}.json" for platform in ("android", "ios")]
    require(all(path.is_file() for path in paths), "publication requires completed Android and iOS provenance")
    stages = [json.loads(path.read_text()) for path in paths]
    for key in ("schema_version", "sdk_revision", "snapshot_sha256", "analysis_script_sha256", "xcode", "host"):
        require(stages[0][key] == stages[1][key], f"split platform provenance differs: {key}")
    require(stages[0].get("python") == stages[1].get("python"), "split Python environments differ")
    merged = dict(stages[0])
    merged["measured_at"] = max(stage["measured_at"] for stage in stages)
    merged["commands"] = [command for stage in stages for command in stage["commands"]]
    merged["stage_measured_at"] = {platform: stage["measured_at"]
                                   for platform, stage in zip(("android", "ios"), stages)}
    merged["artifact_sha256"] = {name: expected for stage in stages
                                 for name, expected in stage.get("artifact_sha256", {}).items()}
    return merged


GENERATED_START = "<!-- sdk-size-analysis:generated:start -->"
GENERATED_END = "<!-- sdk-size-analysis:generated:end -->"


def measurement_identity(evidence):
    inputs = dict(revision=evidence["revision"], native=evidence["native_sha256"],
                  framework=evidence["ios"]["framework"]["sha256"],
                  original=evidence["ios"]["original"]["sha256"],
                  stripped=evidence["ios"]["stripped"]["sha256"],
                  ipa=evidence["ios"]["normalized_ipa"]["sha256"])
    return hashlib.sha256(json.dumps(inputs, sort_keys=True).encode()).hexdigest()


def write_report(path, generated, identity=None):
    stamp = f"<!-- sdk-size-analysis:inputs:{identity} -->\n" if identity else ""
    block = GENERATED_START + "\n" + stamp + generated.rstrip() + "\n" + GENERATED_END
    if not path.exists():
        content = block + "\n\n## Analyst Interpretation\n\n"
    else:
        existing = path.read_text()
        if GENERATED_START in existing or GENERATED_END in existing:
            require(existing.count(GENERATED_START) == existing.count(GENERATED_END) == 1,
                    "report generated markers are missing or duplicated")
            start = existing.index(GENERATED_START)
            stop = existing.index(GENERATED_END) + len(GENERATED_END)
            require(start < stop - len(GENERATED_END), "report generated markers are out of order")
            previous = re.search(r"<!-- sdk-size-analysis:inputs:([^ ]+) -->", existing[start:stop])
            analyst = (existing[:start] + existing[stop:]).replace("## Analyst Interpretation", "").strip()
            require(not (analyst and identity and previous and previous.group(1) != identity),
                    "measurement inputs changed with analyst material present; use a new --report-prefix")
            content = existing[:start] + block + existing[stop:]
        else:
            archive = path.with_name(f"{path.stem}.previous-{digest(path)[:12]}.md")
            if archive.exists():
                require(archive.read_text() == existing, "legacy report archive already differs")
            else:
                archive.write_text(existing)
            heading = "## Source and Disassembly Interpretation"
            interpretation = ""
            if heading in existing:
                start = existing.index(heading)
                following = re.search(r"(?m)^## ", existing[start + len(heading):])
                stop = start + len(heading) + following.start() if following else len(existing)
                interpretation = existing[start:stop].rstrip() + "\n"
            content = (block + "\n\n## Analyst Interpretation\n\n"
                       f"Prior unmarked report preserved in [{archive.name}]({archive.name}). "
                       "The material below is historical interpretation; revalidate it against the new evidence.\n\n"
                       + interpretation)
    path.write_text(content)


def metrics(evidence):
    ios = evidence["ios"]
    return dict(android_raw=evidence["raw_bytes"], android_zip=evidence["zip_bytes"],
                android_executable=evidence["executable_bytes"], android_text=evidence["text_bytes"],
                ios_framework=ios["framework"]["bytes"], ios_stripped=ios["stripped"]["file_bytes"],
                ios_bundle=ios["bundle_bytes"], ios_ipa=ios["normalized_ipa"]["bytes"],
                ios_text=ios["original"]["sections"]["__TEXT,__text"]["bytes"])


def compare_evidence(baseline, candidate):
    controls = ("dependency_sources", "abi", "build_command", "bazel_pin", "host", "xcode", "llvm", "rust_version",
                "ndk_source_properties", "tool_sha256", "zipper_sha256")
    differences = [key for key in controls if baseline["provenance"].get(key) != candidate["provenance"].get(key)]
    for key in ("analysis_script_sha256", "python", "xcode", "host"):
        if baseline["run"].get(key) != candidate["run"].get(key):
            differences.append(f"run.{key}")
    for key in (".bazelrc", ".bazelversion", "MODULE.bazel", "MODULE.bazel.lock", "Cargo.lock",
                "examples/swift/hello_world/BUILD"):
        if baseline["run"]["snapshot_sha256"].get(key) != candidate["run"]["snapshot_sha256"].get(key):
            differences.append(f"snapshot.{key}")
    before, after = metrics(baseline), metrics(candidate)
    return dict(baseline_revision=baseline["revision"], candidate_revision=candidate["revision"],
                matched_controls=not differences, control_differences=differences,
                metrics={key: dict(baseline=value, candidate=after[key], delta=after[key] - value,
                                   percent=100 * (after[key] - value) / value if value else None)
                         for key, value in before.items()})


def write_analysis_packet(output, evidence, baseline=None):
    lines = ["# Analysis Packet", "", f"SDK revision: `{evidence['revision']}`.", "",
             "Artifacts and accounting are verified. Interpret labels using matching pinned source;",
             "they are not independent removal costs or runtime measurements.", "",
             "| Metric | Bytes |", "| --- | ---: |"]
    lines.extend(f"| {key} | {value:,} |" for key, value in metrics(evidence).items())
    lines.extend(["", "## Android Logger Hotspots", "", "| Bytes | Function | Disassembly |",
                  "| ---: | --- | --- |"])
    for index, entry in enumerate(evidence["logger"]["largest"]):
        name = entry["names"][0].replace("|", "&#124;")
        disassembly = f"logger-disassembly-{index:02d}.txt"
        require((output / "android/analysis" / disassembly).is_file(), f"missing hotspot: {disassembly}")
        lines.append(f"| {entry['bytes']:,} | `{name}` | [{disassembly}](android/analysis/{disassembly}) |")
    analysis = json.loads((output / "android/analysis/analysis.json").read_text())
    lines.extend(["", "## Android Logger Direct Calls", "",
                  "Static direct call sites only, not execution frequency or additive callee costs.", "",
                  "Twelve most frequent targets per hotspot; the complete inventory is linked below.", "",
                  "| Hotspot | Sites | Callee Bytes | Target |", "| --- | ---: | ---: | --- |"])
    for index, entry in enumerate(analysis["logger"]["largest"]):
        for target in entry["direct_calls"][:12]:
            names = "; ".join(target["names"]).replace("|", "&#124;") or f"0x{target['address']:x} (unresolved)"
            size = f"{target['callee_bytes']:,}" if target["callee_bytes"] is not None else "unknown"
            lines.append(f"| {index:02d} | {target['call_sites']} | {size} | `{names}` |")
    lines.extend(["", "## Protobuf Reflection Inventory", "",
                  "Named matches only; this does not attribute anonymous constants or inlined code.", "",
                  "| Bytes | Symbol |", "| ---: | --- |"])
    for entry in analysis["descriptor_reflection_symbols"]:
        name = entry["name"].replace("|", "&#124;")
        lines.append(f"| {entry['size']:,} | `{name}` |")
    lines.extend(["", "## iOS Linked Hotspots", "",
                  "Next-address estimates include alignment and anonymous code; the sample includes non-SDK code.", "",
                  "| Estimated Bytes | Function |", "| ---: | --- |"])
    for entry in evidence["ios"]["largest_functions"]:
        name = "; ".join(entry["names"]).replace("|", "&#124;")
        lines.append(f"| {entry['estimated_bytes']:,} | `{name}` |")
    lines.extend(["", "## Detailed Evidence", "",
                  "- [Android sections, categories and call sites](android/analysis/ANALYSIS.md)",
                  "- [Complete Android call-site and symbol inventory](android/analysis/analysis.json)",
                  "- [Frozen logger dependency manifest](android/logger-Cargo.toml)",
                  "- [Unstripped iOS functions and sections](ios/original-analysis.json)",
                  "- [Strip invariants](ios/strip-validation.json)",
                  "- [Static archive member analysis](ios/archive-analysis.json)", ""])
    if (output / "android/logger-source").is_dir():
        lines.extend(["Pinned logger source: [builder](android/logger-source/builder.rs),",
                      "[runtime](android/logger-source/lib.rs). Other modules are in `android/logger-source/`.", ""])
    if baseline:
        comparison = compare_evidence(json.loads(baseline.read_text()), evidence)
        (output / "comparison.json").write_text(json.dumps(comparison, indent=2) + "\n")
        lines.extend(["## Comparison", "", "Controls: " +
                      ("matched." if comparison["matched_controls"] else
                       "UNMATCHED. Do not attribute deltas to a source change. Differences: " +
                       ", ".join(comparison["control_differences"])), "",
                      "| Metric | Baseline | Candidate | Delta | % |", "| --- | ---: | ---: | ---: | ---: |"])
        for key, entry in comparison["metrics"].items():
            percent = f"{entry['percent']:+.2f}" if entry["percent"] is not None else "n/a"
            lines.append(f"| {key} | {entry['baseline']:,} | {entry['candidate']:,} | {entry['delta']:+,} | {percent} |")
    (output / "ANALYSIS.md").write_text("\n".join(lines) + "\n")
    print("Verified measurements: " + "; ".join(f"{key}={value:,}" for key, value in metrics(evidence).items()), flush=True)
    print(f"Analysis packet: {output / 'ANALYSIS.md'}", flush=True)


def report_markdown(evidence):
    ios = evidence["ios"]
    logger = evidence["logger"]
    provenance = evidence["provenance"]
    date = evidence["run"]["measured_at"][:10]
    executable = evidence["executable_bytes"]
    proto = sum(size for category, size in evidence["categories"].items() if category.startswith("Protobuf"))
    largest = sum(entry["bytes"] for entry in logger["largest"])
    lines = ["# Capture SDK Code Size: Pristine Baseline", "",
        f"Measured **{date}** at SDK revision `{evidence['revision']}` with committed dependency pins.",
        "This baseline uses fresh replacement evidence. All Android and iOS artifacts were built",
        "from a clean detached worktree in this run; no historical spike or retained app is an input.", "",
        "## Findings", "",
        f"- Android ARM64: **{evidence['raw_bytes']:,} bytes raw**, **{evidence['zip_bytes']:,} bytes ZIP**",
        f"  ({(evidence['zip_bytes'] + 1023) // 1024:,} CI-style rounded-up KiB).",
        f"- Executable storage: **{executable:,} bytes**; `.text`: **{evidence['text_bytes']:,} bytes**.",
        f"- Logger/orchestration: **{logger['bucket_bytes']:,} bytes ({100 * logger['bucket_bytes'] / executable:.2f}%)**.",
        f"  The eight largest `bd_logger` ranges occupy **{largest:,} bytes**,",
        f"  **{100 * largest / logger['logger_bytes']:.2f}%** of its label, not removable feature costs.",
        f"- Protobuf messages/runtime: **{proto:,} bytes ({100 * proto / executable:.2f}%)**.",
        "  Most message-labeled code is specialized container/support code, not named wire loops.",
        f"- iOS ARM64 static framework: **{ios['framework']['bytes']:,} bytes**.",
        f"  Fresh stripped sample executable: **{ios['stripped']['file_bytes']:,} bytes**;",
        f"  normalized unsigned IPA: **{ios['normalized_ipa']['bytes']:,} bytes**.",
        "  This is a local stripped-app proxy, not signed App Thinning or SDK-only app overhead.", "",
        "## Inputs and Controls", "", "| Input | Value |", "| --- | --- |",
        f"| capture-sdk | `{evidence['revision']}` |"]
    for source in provenance["dependency_sources"]:
        repository = source.split("/")[-1].split(".git")[0]
        lines.append(f"| {repository} | `{source.split('#')[-1]}` |")
    lines.extend([f"| Build host | {provenance['host']} |",
        f"| Xcode | {provenance['xcode'].replace(chr(10), ', ')} |",
        f"| NDK | {provenance.get('ndk_source_properties', '').split('Pkg.Revision = ')[-1].splitlines()[0]} |",
        f"| Analysis LLVM | {provenance['llvm'].split('LLVM version ')[-1].splitlines()[0]} |",
        f"| Registered Rust toolchain | {provenance['rust_version']} |",
        "| Android / iOS architecture | ARM64 / unsigned device ARM64 |",
        "| Native compression | Bazel zipper `cC`; `jni/arm64-v8a/libcapture.so` |",
        f"| Cargo.lock SHA-256 | `{provenance['snapshot_sha256']['Cargo.lock']}` |",
        f"| MODULE.bazel.lock SHA-256 | `{provenance['snapshot_sha256']['MODULE.bazel.lock']}` |",
        f"| Native build ID | `{', '.join(evidence['build_ids'])}` |",
        f"| Native SHA-256 | `{evidence['native_sha256']}` |", "",
        "The evidence records all manifest/lock/BUILD hashes, dependency revisions, tool hashes,",
        "analysis-script hashes and exact commands. Release flags use size optimization, one",
        "codegen unit and fat LTO; Android forces unwind tables and compresses CFI, while iOS",
        "disables Rust unwind tables and uses Swift size/WMO optimization. The SDK MODULE pins",
        "the Rust toolchain; its hash is retained with the controls. `--config=nocache` disables",
        "remote release caching, not local action/disk caching. No caches were cleared.", "",
        "Local macOS ARM64/Xcode measurements are not Linux/x86_64 Android CI bytes or the",
        "separately pinned CI Xcode output. No dependency wiring or user checkout was changed.", ""])

    def table(title, heading, values, denominator=None):
        lines.extend([f"## {title}", "", f"| {heading} | Bytes |" + (" % |" if denominator else ""),
                      "| --- | ---: |" + (" ---: |" if denominator else "")])
        for label, size in values:
            lines.append(f"| {label.replace('|', '&#124;')} | {size:,} |" +
                         (f" {100 * size / denominator:.2f}% |" if denominator else ""))
        lines.append("")

    table("Android Structural Accounting", "File Component",
          [(section["name"], section["bytes"]) for section in evidence["raw_sections"]] +
          [("Headers and padding", evidence["headers_padding_bytes"]), ("**Raw total**", evidence["raw_bytes"])])
    lines.extend([f"NOBITS storage is **{evidence['zero_fill_bytes']:,} bytes**, excluded from the file total.",
        "`.gnu_debugdata` is shipped compressed mini-debug metadata, not full DWARF. The",
        "ZIP metric includes its complete archive overhead; compression is not additive by section.", ""])
    table("Android Executable Breakdown", "Whole-Function Label", evidence["categories"].items(), executable)
    lines.extend(["Each interval has one label; aliases are counted once and gaps remain explicit.",
        "Schema/type references precede generic owners. Whole labeled functions can contain",
        "inlined dependencies, and shared LLVM outliners are separate. These labels are not exact",
        "LTO crate costs or independent feature savings. No DWARF inline provenance is available.", ""])
    table("Protobuf Decomposition", "Function Shape", evidence["protobuf_message_shapes"].items())
    lines.extend([f"Descriptor/reflection inventory: **{evidence['descriptor_reflection_symbol_count']} symbol(s)**.",
        "Full identities are retained in scratch analysis; absence of named descriptor graphs does",
        "not establish an exact zero-byte reflection or constant-data cost. JNI descriptors are excluded.", ""])
    table("Logger Origins", "Origin Label", logger["bucket_origins"].items())
    table("Logger Modules", "Module", logger["modules"].items())
    table("Largest Logger Functions", "Function",
          [(f"`{entry['names'][0]}`", entry["bytes"]) for entry in logger["largest"]])
    table("Logger Direct Dependencies", "Separately Named Dependency", logger["direct_dependency_named_bytes"].items())
    lines.extend(["Dependencies in this table are already outside the logger bucket. Do not charge",
        "their bytes to every caller, multiply them by static calls, or treat them as removal savings.", ""])
    table("iOS Fresh Measurements", "Metric", [
        ("ARM64 static framework", ios["framework"]["bytes"]),
        ("Unsigned sample executable", ios["original"]["file_bytes"]),
        ("Unsigned stripped sample executable", ios["stripped"]["file_bytes"]),
        ("Stripped bundle file bytes", ios["bundle_bytes"]),
        ("Normalized unsigned IPA", ios["normalized_ipa"]["bytes"])])
    table("iOS Static Archive Components", "Largest Member/Component", [
        (entry["name"], entry["archive_bytes"]) for entry in ios["framework"]["archive"]["largest_components"]])
    archive = ios["framework"]["archive"]
    lines.extend([f"Archive member bodies: **{archive['member_bytes']:,} bytes**; archive headers/name storage/padding:",
        f"**{archive['headers_padding_bytes']:,} bytes**. Member `__TEXT,__text` totals **{archive['text_bytes']:,} bytes**.",
        "The Rust LTO member includes dependencies; its name does not make all its bytes bridge code.",
        "Linkable symbol indices, relocations and member metadata are not final app executable costs.", ""])
    text = ios["original"]["sections"]["__TEXT,__text"]["bytes"]
    table("iOS Linked Text Estimates", "Whole-Function Label", ios["original"]["text_categories"].items(), text)
    lines.extend([f"Linked `__TEXT,__text`: **{text:,} bytes**; leading unattributed range:",
        f"**{ios['original']['unattributed_leading_text']:,} bytes**. Includes SDK, sample and support code.",
        "Swift and Objective-C labels are separate from the native catchall. Next-address estimates",
        "include alignment and anonymous trailing instructions; no SDK-free app control was built.", ""])
    table("iOS Stripped File Accounting", "Component", ios["stripped_file_accounting"].items())
    table("iOS Symbol Metadata", "Component", [
        (f"{label} {key}", ios[label]["metadata"][key]) for label in ("original", "stripped")
        for key in ("symbol_records", "symbol_strings")])
    audit = ios["strip_validation"]
    lines.extend([f"Stripping removes **{audit['original_bytes'] - audit['stripped_bytes']:,} file bytes**.",
        f"It preserves all **{audit['sections_preserved']} sections**, loader payloads/dependencies,",
        f"**{audit['undefined_preserved']:,} undefined symbols** and **{audit['indirect_targets_preserved']:,} indirect targets**.",
        f"Symbol count: **{audit['symbols_before']:,} -> {audit['symbols_after']:,}**; exports:",
        f"**{audit['exports_before']:,} -> {audit['exports_after']:,}**, with surviving targets unchanged.",
        "Ordinary Rust symbols are removed and dynamically referenced entries retained. Resource",
        "bytes and normalized ZIP entry sets/CRCs match, and original executable bytes are unchanged.", "",
        f"Framework SHA-256: `{ios['framework']['sha256']}`.",
        f"Unsigned executable SHA-256: `{ios['original']['sha256']}`.",
        f"Stripped executable SHA-256: `{ios['stripped']['sha256']}`.",
        f"Normalized IPA SHA-256: `{ios['normalized_ipa']['sha256']}`.", "",
        "## Verification and Reproduction", "",
        f"Android audits: {evidence['defined_symbols']:,} defined symbols, {evidence['code_symbols']:,} nonzero code symbols;",
        f"text gaps **{evidence['text_gaps_bytes']:,} bytes**, overlapping/alias ranges **{evidence['text_multiple_symbols_bytes']:,} bytes**.",
        ("Full/shipped allocated content, layout, flags and build IDs match. Uploaded debug-map"
         if evidence["audit"]["allocated_content_verified"] else
         "Debug/shipped layouts and build IDs match; NOBITS allocated-byte identity is unverified. Uploaded debug-map"),
        "names, addresses and sizes match, allowing only known debug-only data-class changes.",
        "AAR/native ZIP identity and CRC checks, framework member/size checks, archive analysis,",
        "Mach-O accounting, stripping/resource audits and source snapshot preservation all passed.", "",
        "The executable stripped-app bytes are not a signed export or App Thinning result.",
        "No Rust/FFI behavior changes, device execution, CPU/allocation/throughput measurements,",
        "signed exports, or unrelated SDK Clippy/behavior test validation are claimed.", "",
        f"Run the one-command workflow in [the size-analysis skill]({evidence['report_skill_path']}).",
        "The runner saves fresh Android/iOS artifacts, all logs, sections, symbols, intervals and",
        "eight logger disassemblies under the chosen workspace `.tmp/` directory. It emits a",
        "hash-checked report and [compact evidence](" + evidence["report_evidence_filename"] + ").",
        "Use `--publish-only` to regenerate publication without rebuilding; it checks artifact",
        "hashes and revision/lock agreement first. Exact commands and tool/script hashes are",
        "recorded in the evidence. Existing variants are never overwritten.", "",
        "## Next Investigations, Not Claimed Savings", "",
        "1. Startup/runtime construction and the event loop are the largest logger ranges. Inspect",
        "   their disassembly and matching pinned source before testing a shared non-inlined or",
        "   erased startup boundary, or a cold control-path boundary. New async functions may inline",
        "   again; boxing/no-inline can increase code size and change runtime costs.",
        "2. Type-specialized protobuf support exceeds named wire methods. Test ownership/error",
        "   conversion and specialization boundaries, not just a replacement parser. Preserve API,",
        "   shutdown, ordering and cancellation behavior before measuring matched artifacts.",
        "3. Swift code, reflection/type metadata and static archive symbol/relocation storage need",
        "   separate investigation. Static archive reductions need not reduce a stripped app.",
        "4. Runtime/dependency feature narrowing is a hypothesis, not the sum of named dependency",
        "   bytes. Verify transitive users; retain raw, ZIP, code, data and metadata results for",
        "   each isolated experiment, including rejected regressions.", ""])
    return "\n".join(lines)


def publish_report(output, prefix, sdk_root, baseline=None):
    evidence = json.loads((output / "android/evidence.json").read_text())
    ios = json.loads((output / "ios/evidence.json").read_text())
    run = load_run_provenance(output)
    for name, expected in run.get("artifact_sha256", {}).items():
        verify_artifact(output / name, expected)
    require(evidence["revision"] == ios["revision"] == run["sdk_revision"], "platform SDK revisions differ")
    require(evidence["provenance"]["checkout_status"] == "", "measured SDK was dirty")
    require(sum(evidence["categories"].values()) == evidence["executable_bytes"], "Android code totals differ")
    require(sum(section["bytes"] for section in evidence["raw_sections"]) +
            evidence["headers_padding_bytes"] == evidence["raw_bytes"], "Android file totals differ")
    require(evidence["native_sha256"] == evidence["artifacts"]["binary"]["sha256"], "native evidence identity differs")
    require((output / "android/capture.so").stat().st_size == evidence["raw_bytes"], "native byte count differs")
    require((output / "android/capture.so.zip").stat().st_size == evidence["zip_bytes"], "ZIP byte count differs")
    for name, expected in evidence["provenance"]["snapshot_sha256"].items():
        require(run["snapshot_sha256"][name] == expected, f"platform source snapshot differs: {name}")
    symbols_name = "capture.full.so" if evidence["audit"]["symbols_mode"] == "full" else "capture.debug.elf"
    for key, name in dict(binary="capture.so", symbols_elf=symbols_name, debug_map="capture.debug.gz",
                          aar="capture.aar", zip="capture.so.zip").items():
        verify_artifact(output / "android" / name, evidence["artifacts"][key]["sha256"])
    binary = output / "ios/framework/Capture.xcframework/ios-arm64/Capture.framework/Capture"
    verify_artifact(binary, ios["framework"]["sha256"])
    verify_artifact(output / "ios/unsigned.bazel.ipa", ios["unsigned_ipa_sha256"])
    for label in ("original", "stripped"):
        analysis = json.loads((output / f"ios/{label}-analysis.json").read_text())
        require(analysis["sha256"] == ios[label]["sha256"], f"iOS analysis identity differs: {label}")
        source = output / "ios" / ("unsigned" if label == "original" else "stripped") / "Payload/Bitdrift Sample App.app/hello_world_app"
        verify_artifact(source, analysis["sha256"])
    verify_artifact(output / "ios/stripped.ipa", ios["normalized_ipa"]["sha256"])
    for name, expected in ios["resources_sha256"].items():
        for variant in ("unsigned", "stripped"):
            verify_artifact(output / "ios" / variant / "Payload/Bitdrift Sample App.app" / name, expected)
    for label in ("original", "stripped"):
        require(sum(ios[label]["text_categories"].values()) + ios[label]["unattributed_leading_text"] ==
                ios[label]["sections"]["__TEXT,__text"]["bytes"], "iOS text totals differ")
    components = ios["framework"]["archive"]["components"]
    member_bytes = sum(component["archive_bytes"] for component in components.values())
    require(member_bytes <= ios["framework"]["bytes"], "archive accounting exceeds file size")
    ios["framework"]["archive"] = dict(member_bytes=member_bytes,
        headers_padding_bytes=ios["framework"]["bytes"] - member_bytes,
        text_bytes=sum(component["text_bytes"] for component in components.values()),
        largest_components=[dict(name=name, **component) for name, component in sorted(
            components.items(), key=lambda entry: -entry[1]["archive_bytes"])[:12]])
    backed = sum(section["bytes"] for section in ios["stripped"]["sections"].values() if section["file_backed"])
    linkedit = ios["stripped"]["segments"]["__LINKEDIT"]["file_bytes"]
    residual = ios["stripped"]["file_bytes"] - backed - linkedit
    require(residual >= 0, "negative Mach-O file accounting residual")
    ios["stripped_file_accounting"] = dict(file_backed_sections=backed, linkedit=linkedit, headers_padding=residual)
    run["commands"] = [command.replace(str(sdk_root.resolve()), "<sdk-root>")
        .replace(str(sys.executable), "<venv-python>").replace(str(SCRIPTS.parents[3]), "<analysis-checkout>")
        for command in run["commands"]]
    evidence.update(ios=ios, run=run, report_evidence_filename=prefix.name + ".evidence.json",
                    report_skill_path=os.path.relpath(SCRIPTS.parent / "SKILL.md", prefix.parent.resolve()),
                    publication=dict(published_at=datetime.now(timezone.utc).isoformat(),
                        script_sha256={path.name: digest(path) for path in sorted(SCRIPTS.glob("*.py"))}))
    prefix.parent.mkdir(parents=True, exist_ok=True)
    write_analysis_packet(output, evidence, baseline)
    write_report(prefix.with_suffix(".md"), report_markdown(evidence), measurement_identity(evidence))
    prefix.with_suffix(".evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
    local_prefix = output / "report"
    if prefix.resolve() != local_prefix.resolve():
        local_evidence = dict(evidence, report_evidence_filename="report.evidence.json",
                              report_skill_path=os.path.relpath(SCRIPTS.parent / "SKILL.md", output))
        write_report(local_prefix.with_suffix(".md"), report_markdown(local_evidence), measurement_identity(evidence))
        local_prefix.with_suffix(".evidence.json").write_text(json.dumps(local_evidence, indent=2) + "\n")
    print(f"Published {prefix.with_suffix('.md')} and {prefix.with_suffix('.evidence.json')}", flush=True)


class Runner:
    def __init__(self, args):
        self.root = args.sdk_root.resolve()
        self.output = args.output.resolve()
        require(self.output.is_relative_to(self.root.parent) or self.output.is_relative_to(SCRIPTS.parents[3]),
                "output must be workspace-local")
        require(".tmp" in self.output.parts, "output must be under .tmp/")
        for platform in (("android", "ios") if args.platform == "all" else (args.platform,)):
            require(not (self.output / platform).exists(),
                    f"{platform} output exists; choose a new directory or use --publish-only for completed runs")
        if args.platform == "ios":
            require((self.output / "android-tools.json").is_file(), "run Android collection first")
        self.output.mkdir(parents=True, exist_ok=True)
        self.logs = self.output / "logs" / args.platform
        self.logs.mkdir(parents=True, exist_ok=True)
        self.commands = []
        self.xcode = args.xcode_version
        self.platform = args.platform
        self.flags = ["--noannounce_rc", "--config=ci", "--config=nocache", "--color=no", "--curses=no"]
        self.android_flags = self.flags + ["--config=release-android", f"--xcode_version={self.xcode}",
                                          "--android_platforms=@rules_android//:arm64-v8a"]
        self.ios_flags = self.flags + ["--config=release-ios", f"--xcode_version={self.xcode}",
                                      "--cpu=ios_arm64", "--define=ios_produce_framework_plist=true"]
        self.revision = self.run("revision", ["git", "rev-parse", "HEAD"]).strip()
        require(not self.run("status", ["git", "status", "--porcelain"]).strip(),
                "use a clean detached worktree for pristine measurements")
        self.snapshots = self.snapshot()
        self.script_snapshots = {path.name: digest(path) for path in sorted(SCRIPTS.glob("*.py"))}
        self.requirements_snapshot = digest(SCRIPTS / "requirements.txt")

    def snapshot(self):
        return {name: digest(self.root / name) for name in (
            "Cargo.toml", "Cargo.lock", "MODULE.bazel", "MODULE.bazel.lock", ".bazelrc", ".bazelversion",
            "examples/swift/hello_world/BUILD") if (self.root / name).exists()}

    def run(self, label, arguments, env=None):
        arguments = [str(argument) for argument in arguments]
        self.commands.append(shlex.join(arguments))
        stdout = self.logs / f"{label}.stdout"
        stderr = self.logs / f"{label}.stderr"
        print(f"{label}: running; log {stdout}", flush=True)
        started = time.monotonic()
        with stdout.open("w") as out, stderr.open("w") as err:
            with subprocess.Popen(arguments, cwd=self.root, stdout=out, stderr=err, env=env) as process:
                try:
                    while True:
                        try:
                            exit_code = process.wait(timeout=20)
                            break
                        except subprocess.TimeoutExpired:
                            print(f"{label}: still running ({time.monotonic() - started:.0f}s); see logs", flush=True)
                except BaseException:
                    process.terminate()
                    process.wait()
                    raise
        if exit_code:
            print(stdout.read_text()[-6000:] + stderr.read_text()[-6000:], file=sys.stderr)
            raise RuntimeError(f"{label} failed ({exit_code}); see {stdout} and {stderr}")
        print(f"{label}: PASS ({time.monotonic() - started:.1f}s)", flush=True)
        return stdout.read_text()

    def bazel(self, label, subcommand, flags, *arguments):
        return self.run(label, [self.root / "bazelw", subcommand, *flags, *arguments])

    def output_file(self, label, flags, target, suffix, group=None):
        flags = flags + ([f"--output_groups={group}"] if group else [])
        output = self.bazel(label, "cquery", flags, target, "--output=files")
        return self.root / unique([line for line in output.splitlines() if line.endswith(suffix)], label)

    def android(self):
        destination = self.output / "android"
        require(not destination.exists(), "android output exists; choose a new run directory")
        build_command = [self.root / "bazelw", "build", *self.android_flags,
                         "//:capture_aar", "//:capture.debug_info", "@bazel_tools//tools/zip:zipper",
                         "--output_groups=+objcopy"]
        self.run("android-build", build_command)
        aar = self.output_file("android-aar", self.android_flags, "//:capture_aar", ".aar")
        query = json.loads(self.bazel("android-actions", "aquery", self.android_flags,
                                     "//:capture.debug_info", "--output=jsonproto"))
        inputs = android_inputs(query, "arm64-v8a")
        execution_root = Path(self.bazel("execution-root", "info", [], "execution_root").strip())
        tools = (execution_root / inputs["nm"]).parent
        zipper = execution_root / "external/bazel_tools/tools/zip/zipper/zipper"
        full_elf = execution_root / inputs["full_elf"]
        symbols_mode = "full" if full_elf.is_file() else "debug"
        full_arguments = ["--full-elf", full_elf] if symbols_mode == "full" else []
        if symbols_mode == "debug":
            print("Full ELF unavailable; using uploaded debug symbols with an explicit NOBITS content limitation", flush=True)
        self.run("android-collect", [sys.executable, SCRIPTS / "collect_android.py",
            "--sdk-root", self.root, "--aar", aar, *full_arguments,
            "--debug-map", execution_root / inputs["debug_map"], "--zipper", zipper, "--tools", tools,
            "--output", destination, "--build-command", shlex.join(map(str, build_command))])
        manifests = list((execution_root / "external").glob("*/bd-logger/Cargo.toml"))
        logger = Path(unique([str(path) for path in manifests], "pinned logger manifest"))
        self.run("android-analysis", [sys.executable, SCRIPTS / "elf_analysis.py",
            "--binary", destination / "capture.so", "--symbols-elf",
            destination / ("capture.full.so" if symbols_mode == "full" else "capture.debug.elf"),
            "--symbols-mode", symbols_mode,
            "--debug-map", destination / "capture.debug.gz", "--aar", destination / "capture.aar",
            "--zip", destination / "capture.so.zip", "--tools", tools,
            "--output", destination / "analysis", "--label", "Pristine Android ARM64",
            "--revision", self.revision, "--logger-manifest", logger,
            "--provenance", destination / "provenance.json", "--evidence-output", destination / "evidence.json"])
        ndk_properties = Path(unique([str(parent / "source.properties") for parent in tools.parents
                          if (parent / "source.properties").is_file()], "NDK source.properties"))
        for name, source in (("logger-Cargo.toml", logger), ("ndk-source.properties", ndk_properties)):
            require(source.exists(), f"missing provenance input: {source}")
            shutil.copy2(source, destination / name)
        shutil.copytree(logger.parent / "src", destination / "logger-source")
        (self.output / "android-tools.json").write_text(json.dumps(
            dict(nm=str((tools / "llvm-nm").resolve()), zipper=str(zipper.resolve())), indent=2) + "\n")

    def ios(self):
        from validate_stripped_macho import validate

        destination = self.output / "ios"
        require(not destination.exists(), "ios output exists; choose a new run directory")
        destination.mkdir()
        self.bazel("ios-framework-build", "build", self.ios_flags, "//:ios_dist")
        distribution = self.output_file("ios-distribution", self.ios_flags, "//:ios_dist", ".zip")
        shutil.copy2(distribution, destination / "Capture.ios.zip")
        with zipfile.ZipFile(destination / "Capture.ios.zip") as archive:
            require(archive.testzip() is None, "framework ZIP CRC failure")
            archive.extractall(destination / "framework")
        framework_root = destination / "framework/Capture.xcframework"
        binary = framework_root / "ios-arm64/Capture.framework/Capture"
        self.run("ios-archive-members", [self.root / "ci/check_ios_xcframework_archive_members.sh"],
                 env=dict(os.environ, IOS_CAPTURE_XCFRAMEWORK_ROOT=str(framework_root)))
        self.run("ios-framework-size", [self.root / "ci/capture_ios_binary_size.sh"],
                 env=dict(os.environ, IOS_CAPTURE_BINARY_PATH=str(binary)))
        breakdown = json.loads(self.run("ios-archive-analysis", [sys.executable,
            self.root / "ci/capture_ios_archive_breakdown.py", binary]))
        (destination / "archive-analysis.json").write_text(json.dumps(breakdown, indent=2) + "\n")
        build = self.root / "examples/swift/hello_world/BUILD"
        with unsigned_probe(build, destination / "app.BUILD.original"):
            self.bazel("ios-app-build", "build", self.ios_flags, "--features=disable_legacy_signing",
                       "//examples/swift/hello_world:ios_app_size", "@bazel_tools//tools/zip:zipper")
            ipa = self.output_file("ios-app-ipa", self.ios_flags + ["--features=disable_legacy_signing"],
                                  "//examples/swift/hello_world:ios_app_size", ".ipa")
            shutil.copy2(ipa, destination / "unsigned.bazel.ipa")
        with zipfile.ZipFile(destination / "unsigned.bazel.ipa") as archive:
            require(archive.testzip() is None, "unsigned IPA CRC failure")
            archive.extractall(destination / "unsigned")
        app = Path(unique([str(path) for path in (destination / "unsigned/Payload").glob("*.app")], "sample app"))
        executable = app / "hello_world_app"
        original_hash = digest(executable)
        stripped = destination / "stripped/Payload" / app.name
        shutil.copytree(app, stripped)
        require(not (app / "_CodeSignature").exists(), "unexpected signed app")
        profile = stripped / "embedded.mobileprovision"
        require(profile.exists() and profile.stat().st_size == 0, "expected empty unsigned probe profile")
        profile.unlink()
        self.run("ios-strip", ["xcrun", "strip", "-u", "-r", stripped / executable.name])
        validation = validate(executable, stripped / executable.name)
        (destination / "strip-validation.json").write_text(json.dumps(validation, indent=2) + "\n")
        require(digest(executable) == original_hash, "original executable changed")
        resources = {str(path.relative_to(app)): digest(path) for path in app.rglob("*")
                     if path.is_file() and path.name not in (executable.name, "embedded.mobileprovision")}
        stripped_resources = {str(path.relative_to(stripped)): digest(path) for path in stripped.rglob("*")
                              if path.is_file() and path.name != executable.name}
        require(resources == stripped_resources, "stripped app resources changed")
        execution_root = Path(self.bazel("ios-execution-root", "info", [], "execution_root").strip())
        zipper = execution_root / "external/bazel_tools/tools/zip/zipper/zipper"
        tool_record = self.output / "android-tools.json"
        require(tool_record.exists(), "run Android collection first to resolve Rust-demangling NDK llvm-nm")
        nm = Path(json.loads(tool_record.read_text())["nm"])
        for label, source in (("original", executable), ("stripped", stripped / executable.name)):
            self.run(f"ios-{label}-analysis", [sys.executable, SCRIPTS / "macho_analysis.py", source,
                "--nm", nm, "--output", destination / f"{label}-analysis.json"])
        entries = {str(path.relative_to(destination / "stripped")): path for path in stripped.rglob("*") if path.is_file()}
        normalized = destination / "stripped.ipa"
        self.run("ios-normalize", [zipper, "cC", normalized, *[f"{name}={entries[name]}" for name in sorted(entries)]])
        with zipfile.ZipFile(normalized) as archive:
            require(archive.namelist() == sorted(entries), "normalized IPA entries differ")
            require(archive.testzip() is None, "normalized IPA CRC failure")
            for name, path in entries.items():
                require(archive.read(name) == path.read_bytes(), f"normalized resource differs: {name}")
        original_analysis = json.loads((destination / "original-analysis.json").read_text())
        stripped_analysis = json.loads((destination / "stripped-analysis.json").read_text())
        evidence = dict(schema_version=1, revision=self.revision, measurement="fresh unsigned ARM64 stripped proxy",
            framework=dict(bytes=binary.stat().st_size, sha256=digest(binary), archive=breakdown),
            original={key: value for key, value in original_analysis.items() if key != "functions"},
            stripped={key: value for key, value in stripped_analysis.items() if key != "functions"},
            largest_functions=original_analysis["functions"][:12], strip_validation=validation,
            bundle_bytes=sum(path.stat().st_size for path in entries.values()),
            normalized_ipa=dict(bytes=normalized.stat().st_size, sha256=digest(normalized), entries=sorted(entries)),
            resources_sha256=resources, unsigned_ipa_sha256=digest(destination / "unsigned.bazel.ipa"))
        (destination / "evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print(f"iOS: framework={binary.stat().st_size:,}; stripped app={validation['stripped_bytes']:,}; "
              f"normalized IPA={normalized.stat().st_size:,}", flush=True)

    def finish(self):
        require(self.snapshot() == self.snapshots, "SDK manifests/locks/BUILD changed during measurement")
        require({path.name: digest(path) for path in sorted(SCRIPTS.glob("*.py"))} == self.script_snapshots,
            "analysis scripts changed during measurement; start a new run")
        require(digest(SCRIPTS / "requirements.txt") == self.requirements_snapshot,
            "analysis requirements changed during measurement; start a new run")
        require(not self.run("final-status", ["git", "status", "--porcelain"]).strip(), "measured checkout is dirty")
        provenance = dict(schema_version=1, sdk_revision=self.revision,
            measured_at=datetime.now(timezone.utc).isoformat(), snapshot_sha256=self.snapshots,
            analysis_script_sha256={path.name: digest(path) for path in sorted(SCRIPTS.glob("*.py"))},
            python=dict(version=sys.version, requirements_sha256=self.requirements_snapshot,
                        packages={line.split("==")[0]: importlib.metadata.version(line.split("==")[0])
                                  for line in (SCRIPTS / "requirements.txt").read_text().splitlines()
                                  if line and not line.startswith("#")}),
            artifact_sha256={str(path.relative_to(self.output)): digest(path)
                             for platform in (("android", "ios") if self.platform == "all" else (self.platform,))
                             for path in sorted((self.output / platform).rglob("*")) if path.is_file()},
            xcode=self.run("xcode", ["xcodebuild", "-version"]).strip(),
            host=self.run("host", ["uname", "-sm"]).strip(), commands=self.commands)
        if self.platform in ("all", "android"):
            provenance["artifact_sha256"]["android-tools.json"] = digest(self.output / "android-tools.json")
        (self.output / f"run-provenance.{self.platform}.json").write_text(json.dumps(provenance, indent=2) + "\n")
        print(f"PASS: evidence and logs in {self.output}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--xcode-version", required=True)
    parser.add_argument("--platform", choices=("all", "android", "ios"), default="all")
    parser.add_argument("--report-prefix", type=Path, help="publish report/evidence here (default: output/report)")
    parser.add_argument("--baseline", type=Path, help="compare with explicit compact baseline evidence")
    parser.add_argument("--publish-only", action="store_true", help="verify frozen artifacts and publish without building")
    args = parser.parse_args()
    if args.publish_only:
        publish_report(args.output.resolve(), args.report_prefix or args.output / "report", args.sdk_root, args.baseline)
        return
    runner = Runner(args)
    if args.platform in ("all", "android"):
        runner.android()
    if args.platform in ("all", "ios"):
        runner.ios()
    runner.finish()
    if args.platform == "all":
        publish_report(args.output.resolve(), args.report_prefix or args.output / "report", args.sdk_root, args.baseline)


if __name__ == "__main__":
    main()
