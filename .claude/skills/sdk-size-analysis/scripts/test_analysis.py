import json
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import unquote

from categories import PROTO_REFLECTION, classify
from elf_analysis import label_debug_disassembly, logger_analysis, partition_text, symbol_identity, verify_debug_symbols
from macho_analysis import classify_macho
from measure import archive_partial, completed_stage, recover_unsigned_probe, script_hashes, setup_python
from run_analysis import (android_inputs, compare_evidence, load_run_provenance, report_markdown, unsigned_build,
                          unsigned_probe, verify_artifact, write_analysis_packet, write_report)


def symbol(address, size, name):
    return dict(address=address, size=size, kind="t", name=name)


class AccountingTests(unittest.TestCase):
    def test_exact_partition(self):
        entries = partition_text([symbol(100, 8, "bd_logger::builder::build"),
                                  symbol(108, 12, "tokio::runtime::run")], 100, 20)
        self.assertEqual([entry["size"] for entry in entries], [8, 12])
        self.assertEqual(logger_analysis(entries, None)["logger_bytes"], 8)

    def test_same_owner_aliases_are_not_double_counted(self):
        entries = partition_text([symbol(100, 20, "bd_logger::builder::build"),
                                  symbol(100, 20, "bd_logger::builder::alias")], 100, 20)
        self.assertEqual(sum(entry["size"] for entry in entries), 20)
        self.assertEqual(logger_analysis(entries, None)["logger_bytes"], 20)
        self.assertEqual(entries[0]["symbol_count"], 2)

    def test_conflicting_overlap_is_explicit(self):
        entries = partition_text([symbol(100, 15, "bd_logger::builder::build"),
                                  symbol(110, 10, "tokio::runtime::run")], 100, 20)
        self.assertEqual([entry["size"] for entry in entries], [10, 5, 5])
        self.assertEqual(entries[1]["category"], "Mixed category aliases")

    def test_gaps_and_clipping(self):
        entries = partition_text([symbol(95, 10, "tokio::runtime::run"),
                                  symbol(115, 10, "bd_logger::builder::build")], 100, 20)
        self.assertEqual([entry["size"] for entry in entries], [5, 10, 5])
        self.assertEqual(entries[1]["category"], "Unattributed text")

    def test_no_symbols(self):
        self.assertEqual(partition_text([], 100, 20)[0]["size"], 20)

    def test_debug_disassembly_replaces_misleading_export_labels(self):
        entries = partition_text([symbol(100, 8, "bd_logger::builder::build"),
                                  symbol(108, 12, "tokio::runtime::run")], 100, 20)
        output = ("0000000000000010 <Java_unrelated_export>:\n"
                  "  64: 94000002 bl 0x6c <Java_unrelated_export+0x5c>\n"
                  "  68: 14000002 b 0x70 <Java_unrelated_export+0x60>\n"
                  "  6c: 94000002 bl 0x80 <Java_unrelated_export+0x70>\n")
        labeled = label_debug_disassembly(output, entries[0], entries)
        self.assertIn("Hotspot: bd_logger::builder::build", labeled)
        self.assertIn("bl 0x6c <tokio::runtime::run>", labeled)
        self.assertIn("b 0x70 <tokio::runtime::run+0x4>", labeled)
        self.assertIn("bl 0x80 <unresolved>", labeled)
        self.assertNotIn("Java_unrelated_export", labeled)

    def test_debug_identity_is_order_independent(self):
        entries = [symbol(100, 8, "one"), symbol(108, 12, "two")]
        self.assertEqual(symbol_identity(entries), symbol_identity(list(reversed(entries))))

    def test_debug_only_data_class_change_is_allowed(self):
        original = dict(address=100, size=8, kind="d", name="data")
        uploaded = dict(original, kind="b")
        verify_debug_symbols([original], [uploaded])

    def test_debug_symbol_damage_is_rejected(self):
        original = symbol(100, 8, "one")
        for uploaded in (dict(original, address=101), dict(original, size=9), dict(original, kind="b")):
            with self.assertRaises(ValueError):
                verify_debug_symbols([original], [uploaded])

    def test_schema_rule_precedes_generic_container_owner(self):
        self.assertEqual(classify("alloc::vec::Vec<bd_proto::protos::client::api::ApiResponse>::drop")[0],
                         "Protobuf messages and type-specialized support")

    def test_sdk_reference_precedes_std_wrapper(self):
        self.assertEqual(classify("std::thread::spawn<bd_logger::builder::Builder>")[1], "bd_logger")

    def test_reflection_inventory_ignores_jni_descriptors(self):
        self.assertIsNone(PROTO_REFLECTION.search("jni::wrapper::descriptors::desc::Desc::lookup"))
        self.assertIsNotNone(PROTO_REFLECTION.search("protobuf::reflect::error::ReflectError::fmt"))
        self.assertIsNotNone(PROTO_REFLECTION.search("bd_proto::protos::api::file_descriptor"))


class BaselineReportTests(unittest.TestCase):
    def test_durable_report_matches_accounting_evidence(self):
        root = Path(__file__).resolve().parents[4]
        evidence = json.loads((root / "docs/sdk-code-size-2026-10-08.evidence.json").read_text())
        report = (root / "docs/sdk-code-size-2026-10-08.md").read_text()
        self.assertEqual(sum(evidence["categories"].values()), evidence["executable_bytes"])
        self.assertEqual(sum(section["bytes"] for section in evidence["raw_sections"]) +
                         evidence["headers_padding_bytes"], evidence["raw_bytes"])
        self.assertEqual(sum(evidence["logger"]["modules"].values()), evidence["logger"]["logger_bytes"])
        for category, size in evidence["categories"].items():
            self.assertIn(f"| {category} | {size:,} | ", report)
        for module, size in evidence["logger"]["modules"].items():
            self.assertIn(f"| {module} | {size:,} |", report)
        for key in ("raw_bytes", "zip_bytes", "text_bytes", "executable_bytes"):
            self.assertIn(f"{evidence[key]:,}", report)
        self.assertEqual(evidence["provenance"]["checkout_status"], "")
        self.assertEqual(evidence["revision"], evidence["provenance"]["sdk_revision"])
        self.assertRegex(evidence["revision"], r"^[0-9a-f]{40}$")
        self.assertIn(evidence["revision"], report)

    def test_ios_report_and_evidence_reconcile(self):
        root = Path(__file__).resolve().parents[4]
        evidence = json.loads((root / "docs/sdk-code-size-2026-10-08.evidence.json").read_text())
        report = (root / "docs/sdk-code-size-2026-10-08.md").read_text()
        ios = evidence["ios"]
        self.assertEqual(ios["revision"], evidence["revision"])
        for label in ("original", "stripped"):
            self.assertEqual(sum(ios[label]["text_categories"].values()) + ios[label]["unattributed_leading_text"],
                             ios[label]["sections"]["__TEXT,__text"]["bytes"])
            self.assertIn(f"{ios[label]['file_bytes']:,}", report)
        self.assertEqual(sum(ios["stripped_file_accounting"].values()), ios["stripped"]["file_bytes"])
        self.assertIn(f"{ios['normalized_ipa']['bytes']:,}", report)
        self.assertIn(f"{ios['framework']['bytes']:,}", report)
        for category, size in ios["original"]["text_categories"].items():
            self.assertIn(f"| {category} | {size:,} | ", report)


class RunnerTests(unittest.TestCase):
    def test_bootstrap_installs_only_missing_or_mismatched_pins(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            base = Path(temporary)
            python = base / "venv/bin/python"
            python.parent.mkdir(parents=True)
            python.touch()
            pins = dict(line.split("==") for line in (root / ".claude/skills/sdk-size-analysis/scripts/requirements.txt")
                        .read_text().splitlines() if line and not line.startswith("#"))
            with patch("measure.BASE", base), patch("measure.execute", side_effect=[json.dumps(pins), ""]) as execute:
                self.assertEqual(setup_python(base), python)
                self.assertNotIn("python-install", [call.args[0] for call in execute.call_args_list])
            with patch("measure.BASE", base), patch("measure.execute", side_effect=["{}", "", ""]) as execute:
                setup_python(base)
                self.assertIn("python-install", [call.args[0] for call in execute.call_args_list])

    def test_skill_frontmatter_and_local_document_links(self):
        root = Path(__file__).resolve().parents[4]
        skill = root / ".claude/skills/sdk-size-analysis"
        frontmatter = (skill / "SKILL.md").read_text().split("---", 2)[1]
        self.assertRegex(frontmatter, r"(?m)^name: sdk-size-analysis$")
        self.assertRegex(frontmatter, r"(?m)^description:")
        documents = [skill / "SKILL.md", root / "docs/sdk-size-comparison.md", *skill.glob("references/*.md")]
        for document in documents:
            for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", document.read_text()):
                if "://" in target:
                    continue
                filename, _, anchor = unquote(target).partition("#")
                linked = document.parent / filename if filename else document
                self.assertTrue(linked.exists(), f"{document}: missing link {target}")
                if anchor:
                    headings = re.findall(r"(?m)^#+ (.+)$", linked.read_text())
                    slugs = [re.sub(r"[^\w -]", "", heading.lower()).replace(" ", "-") for heading in headings]
                    self.assertIn(anchor, slugs, f"{document}: missing anchor {target}")

    def test_completed_stage_verifies_script_lineage_and_artifacts(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            output = Path(temporary)
            artifact = output / "artifact"
            artifact.write_bytes(b"original")
            from collect_android import digest
            receipt = dict(sdk_revision="revision", analysis_script_sha256=script_hashes(),
                           artifact_sha256={"artifact": digest(artifact)})
            (output / "run-provenance.android.json").write_text(json.dumps(receipt))
            self.assertTrue(completed_stage(output, "android", "revision"))
            artifact.write_bytes(b"damaged")
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                completed_stage(output, "android", "revision")
            receipt["analysis_script_sha256"] = {}
            (output / "run-provenance.android.json").write_text(json.dumps(receipt))
            with self.assertRaisesRegex(ValueError, "scripts changed"):
                completed_stage(output, "android", "revision")

    def test_interrupted_unsigned_probe_recovery_refuses_user_edits(self):
        root = Path(__file__).resolve().parents[4]
        original = (root / "examples/swift/hello_world/BUILD").read_bytes()
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            directory = Path(temporary)
            build = directory / "sdk/examples/swift/hello_world/BUILD"
            backup = directory / "output/ios/app.BUILD.original"
            build.parent.mkdir(parents=True)
            backup.parent.mkdir(parents=True)
            backup.write_bytes(original)
            build.write_text(unsigned_build(original.decode()))
            with patch("measure.subprocess.check_output", return_value=original):
                recover_unsigned_probe(directory / "sdk", directory / "output")
                self.assertEqual(build.read_bytes(), original)
                build.write_text("user changes")
                with self.assertRaisesRegex(ValueError, "refusing to overwrite"):
                    recover_unsigned_probe(directory / "sdk", directory / "output")
                self.assertEqual(build.read_text(), "user changes")

    def test_partial_attempt_is_archived_without_reverting_other_stages(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            output = Path(temporary)
            (output / "android").mkdir()
            (output / "android/partial").write_text("retained")
            (output / "ios").mkdir()
            (output / "ios/completed").write_text("unchanged")
            archive_partial(output, "android")
            self.assertFalse((output / "android").exists())
            self.assertEqual(next((output / "failures").glob("*/artifacts/partial")).read_text(), "retained")
            self.assertEqual((output / "ios/completed").read_text(), "unchanged")
            self.assertFalse(completed_stage(output, "android", "revision"))

    def test_split_provenance_merges_only_matching_inputs(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            output = Path(temporary)
            common = dict(schema_version=1, sdk_revision="revision", snapshot_sha256={"lock": "hash"},
                          analysis_script_sha256={"runner": "hash"}, xcode="version", host="host")
            for platform, date in (("android", "2026-10-09T01:00:00Z"), ("ios", "2026-10-09T02:00:00Z")):
                (output / f"run-provenance.{platform}.json").write_text(json.dumps(
                    dict(common, measured_at=date, commands=[platform], artifact_sha256={platform: "hash"})))
            merged = load_run_provenance(output)
            self.assertEqual(merged["commands"], ["android", "ios"])
            self.assertEqual(merged["measured_at"], "2026-10-09T02:00:00Z")
            self.assertEqual(merged["artifact_sha256"], {"android": "hash", "ios": "hash"})
            (output / "run-provenance.ios.json").write_text(json.dumps(
                dict(common, sdk_revision="other", measured_at="date", commands=[])))
            with self.assertRaisesRegex(ValueError, "sdk_revision"):
                load_run_provenance(output)

    def test_report_preserves_analyst_material_on_republication(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            report = Path(temporary) / "report.md"
            write_report(report, "# Generated\n\nold numbers\n")
            report.write_text(report.read_text() + "Observed behavior and proposed experiment.\n")
            write_report(report, "# Generated\n\nnew numbers\n")
            self.assertIn("new numbers", report.read_text())
            self.assertNotIn("old numbers", report.read_text())
            self.assertIn("Observed behavior and proposed experiment.", report.read_text())

    def test_packet_exposes_computed_calls_reflection_and_ios_hotspots(self):
        root = Path(__file__).resolve().parents[4]
        evidence = json.loads((root / "docs/sdk-code-size-2026-10-08.evidence.json").read_text())
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            output = Path(temporary)
            analysis = output / "android/analysis"
            analysis.mkdir(parents=True)
            for index, entry in enumerate(evidence["logger"]["largest"]):
                (analysis / f"logger-disassembly-{index:02d}.txt").touch()
            (analysis / "analysis.json").write_text(json.dumps(dict(
                logger=dict(largest=[dict(direct_calls=[dict(address=100, call_sites=13,
                    names=["protobuf::read_varint"], callee_bytes=236)])]),
                descriptor_reflection_symbols=[dict(name="protobuf::reflect::ReflectError::fmt", size=560)])))
            write_analysis_packet(output, evidence)
            packet = (output / "ANALYSIS.md").read_text()
            self.assertIn("| 00 | 13 | 236 | `protobuf::read_varint` |", packet)
            self.assertIn("| 560 | `protobuf::reflect::ReflectError::fmt` |", packet)
            self.assertIn(evidence["ios"]["largest_functions"][0]["names"][0], packet)

    def test_report_refuses_stale_interpretation_and_malformed_markers(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            report = Path(temporary) / "report.md"
            write_report(report, "original", "first")
            report.write_text(report.read_text() + "Analysis bound to original binaries.\n")
            original = report.read_text()
            with self.assertRaisesRegex(ValueError, "inputs changed"):
                write_report(report, "replacement", "different")
            self.assertEqual(report.read_text(), original)
            report.write_text("<!-- sdk-size-analysis:generated:start -->\npartial")
            with self.assertRaisesRegex(ValueError, "markers"):
                write_report(report, "replacement")

    def test_comparison_calculates_deltas_and_flags_control_changes(self):
        root = Path(__file__).resolve().parents[4]
        baseline = json.loads((root / "docs/sdk-code-size-2026-10-08.evidence.json").read_text())
        candidate = json.loads(json.dumps(baseline))
        candidate["raw_bytes"] -= 12
        comparison = compare_evidence(baseline, candidate)
        self.assertTrue(comparison["matched_controls"])
        self.assertEqual(comparison["metrics"]["android_raw"]["delta"], -12)
        candidate["run"]["xcode"] = "different"
        self.assertIn("run.xcode", compare_evidence(baseline, candidate)["control_differences"])

    def test_legacy_report_is_archived_and_interpretation_migrated(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            report = Path(temporary) / "report.md"
            original = ("# Old\n\nold numbers\n\n## Source and Disassembly Interpretation\n\nMy findings.\n"
                        "\n## Workflow Corrections\n\nObsolete workflow.\n"
                        "\n## Next Investigations, Not Claimed Savings\n\nOld proposals.\n")
            report.write_text(original)
            write_report(report, "# New\n\nnew numbers\n")
            self.assertEqual(next(report.parent.glob("report.previous-*.md")).read_text(), original)
            self.assertIn("My findings.", report.read_text())
            self.assertNotIn("old numbers", report.read_text())
            self.assertNotIn("Obsolete workflow.", report.read_text())
            self.assertNotIn("Old proposals.", report.read_text())

    def test_debug_only_report_discloses_unverified_content(self):
        root = Path(__file__).resolve().parents[4]
        evidence = json.loads((root / "docs/sdk-code-size-2026-10-08.evidence.json").read_text())
        evidence["audit"]["allocated_content_verified"] = False
        report = report_markdown(evidence)
        self.assertIn("NOBITS allocated-byte identity is unverified", report)
        self.assertNotIn("Full/shipped allocated content", report)

    def test_unsigned_probe_restores_build_after_failure(self):
        root = Path(__file__).resolve().parents[4]
        original = (root / "examples/swift/hello_world/BUILD").read_bytes()
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            build = Path(temporary) / "BUILD"
            backup = Path(temporary) / "BUILD.original"
            build.write_bytes(original)
            with self.assertRaisesRegex(RuntimeError, "probe failure"):
                with unsigned_probe(build, backup):
                    self.assertNotEqual(build.read_bytes(), original)
                    raise RuntimeError("probe failure")
            self.assertEqual(build.read_bytes(), original)
            self.assertEqual(backup.read_bytes(), original)

    def test_publication_rejects_changed_artifact(self):
        root = Path(__file__).resolve().parents[4]
        (root / ".tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root / ".tmp") as temporary:
            artifact = Path(temporary) / "native.so"
            artifact.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "artifact hash mismatch"):
                verify_artifact(artifact, "0" * 64)

    def test_macho_swift_is_not_native_catchall(self):
        self.assertEqual(classify_macho("Capture.Logger.log()", "_$s7Capture6LoggerC3logyyF")[0],
                         "Swift functions and type-specialized support")
        self.assertEqual(classify_macho("-[Capture log]", "-[Capture log]")[0], "Objective-C methods")
        self.assertEqual(classify_macho("platform_shared::log", "_rust_symbol")[0], "Platform bridge")

    def test_action_discovery_uses_graph_not_transition_names(self):
        paths = ["arbitrary-transition/arm64-v8a.debug.gz", "other-transition/libcapture.so",
                 "external/ndk/toolchains/llvm/prebuilt/darwin/bin/llvm-nm"]
        document = dict(pathFragments=[dict(id=index, label=path) for index, path in enumerate(paths, 1)],
            artifacts=[dict(id=index, pathFragmentId=index) for index in range(1, 4)],
            depSetOfFiles=[dict(id=1, directArtifactIds=[2], transitiveDepSetIds=[2]),
                           dict(id=2, directArtifactIds=[3])],
            actions=[dict(outputIds=[1], inputDepSetIds=[1])])
        self.assertEqual(android_inputs(document, "arm64-v8a"),
                         dict(debug_map=paths[0], full_elf=paths[1], nm=paths[2]))

    def test_unsigned_probe_only_changes_size_target(self):
        root = Path(__file__).resolve().parents[4]
        original = (root / "examples/swift/hello_world/BUILD").read_text()
        changed = unsigned_build(original)
        self.assertEqual(changed.count('provisioning_profile = ":unsigned_size_profile",'), 1)
        self.assertEqual(changed.count('"//bazel:ios_device_build": "//bazel/ios:ios_provisioning_profile",'), 2)
        self.assertEqual(changed[changed.index("\nxcarchive("):], original[original.index("\nxcarchive("):])


if __name__ == "__main__":
    unittest.main()
