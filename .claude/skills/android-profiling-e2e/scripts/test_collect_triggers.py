import hashlib
import copy
import pathlib
import tempfile
import unittest
from unittest.mock import patch

from collect_triggers import (
    CLOCK_TOLERANCE_NS, MAX_BYTES, check_matcher, deliveries, fixture_report_id,
    matcher_input, process_context, verify,
)


def saved(name="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.perfetto-trace", digest="b" * 64, size=1, kind=2):
    return {
        "event": "profile_saved",
        "observer": {"run_id": "observer-not-source"},
        "details": {"file": name, "sha256": digest, "raw_bytes": size, "trigger_type": kind},
    }

def launch(pid=42, run_id="source"):
    return {"event": "launch", "observer": {
        "installation_id": "installation-a", "run_id": run_id, "pid": pid,
        "boot_count": 1, "wall_ms": 1_700_000_010_000, "elapsed_ns": 10_000_000_000,
        "start_elapsed_ms": 1000, "version_code": 684,
    }}


def profile():
    return {"candidate_read_overflow": False, "trace_processor": {
        "bounds": [{"start_ts": "10000000000", "end_ts": "20000000000"}],
        "clocks": [
            {"clock_name": "REALTIME", "min_offset_ns": "1700000000000000000",
             "max_offset_ns": "1700000000000000000"},
            {"clock_name": "BOOTTIME", "min_offset_ns": "0", "max_offset_ns": "0"},
        ],
        "pid_observations": [{"upid": "1", "pid": "42", "name": None, "start_ts": None,
                              "end_ts": None, "first_seen": "11000000000", "last_seen": "19000000000"}],
    }}


class TriggerEvidenceTests(unittest.TestCase):
    def test_matcher_fixture_preserves_source_scope_and_known_end(self):
        source = launch()
        observer = launch(pid=99, run_id="observer")["observer"]
        exit_row = {"event": "exit_info", "observer": source["observer"],
                    "details": {"source_pid": 42, "timestamp_ms": 1_700_000_015_000}}
        p = dict(profile(), trigger_type=7)
        result = matcher_input(p, observer, [source, source, exit_row])
        self.assertEqual(result["kind"], "oom")
        self.assertEqual(len(result["reports"]), 1)
        candidate = result["reports"][0]
        self.assertEqual(candidate["report_id"], fixture_report_id("source"))
        self.assertEqual(candidate["run"]["pid"], 42)
        self.assertEqual(candidate["run"]["end_elapsed_ns"], 15_000_000_000)
        self.assertEqual(candidate["event_elapsed_ns"], 15_000_000_000)

    def test_matcher_disagreement_and_execution_failure_are_explicit(self):
        scratch = pathlib.Path(__file__).resolve().parents[4] / ".tmp"
        scratch.mkdir(exist_ok=True)
        source = launch()
        p = dict(profile(), trigger_type=2)
        p["process_context"] = process_context(p, source["observer"], [source])
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            path = pathlib.Path(directory) / "trace.perfetto-trace"
            with patch("collect_triggers.subprocess.run") as run:
                run.return_value.returncode = 0
                run.return_value.stdout = '{"report_ids": []}'
                with self.assertRaisesRegex(ValueError, "disagreement"):
                    check_matcher(p, source["observer"], [source], path, pathlib.Path("matcher"))
                run.return_value.returncode = 1
                run.return_value.stderr = "unsupported clock"
                with self.assertRaisesRegex(ValueError, "unsupported clock"):
                    check_matcher(p, source["observer"], [source], path, pathlib.Path("matcher"))
                run.return_value.returncode = 0
                run.return_value.stdout = '{"report_ids": ["' + fixture_report_id("source") + '"]}'
                self.assertEqual(
                    check_matcher(p, source["observer"], [source], path, pathlib.Path("matcher"))["status"],
                    "matched_reference",
                )
                self.assertTrue(path.with_suffix(".matcher.json").is_file())
            p["process_context"]["status"] = "missing_clock_mapping"
            with self.assertRaisesRegex(ValueError, "incomplete host reference"):
                check_matcher(p, source["observer"], [source], path, pathlib.Path("matcher"))

    def test_delayed_observer_and_current_build_do_not_replace_source_pid(self):
        source = launch()
        observer = launch(pid=99, run_id="observer")["observer"]
        observer["version_code"] = 685
        result = process_context(profile(), observer, [source])
        self.assertEqual(result["status"], "matched")
        match = result["matches"][0]
        self.assertEqual(match["pid"], 42)
        self.assertEqual(match["source_version_code"], 684)
        self.assertEqual(match["lookup_version_code"], 685)
        self.assertFalse(match["observer_is_source"])

    def test_multiple_contained_processes_match_without_unique_parent_rule(self):
        p = profile()
        second = dict(p["trace_processor"]["pid_observations"][0], pid="43", upid="2")
        p["trace_processor"]["pid_observations"].append(second)
        result = process_context(p, launch()["observer"], [launch(), launch(43, "other")])
        self.assertEqual([m["pid"] for m in result["matches"]], [42, 43])

    def test_wrong_pid_installation_and_boot_epoch_do_not_match(self):
        for field, value in (("pid", 43), ("installation_id", "installation-b"),
                             ("wall_ms", 1_700_000_070_000), ("boot_count", -1)):
            source = launch()
            source["observer"][field] = value
            with self.subTest(field=field):
                self.assertEqual(process_context(profile(), launch()["observer"], [source])["matches"], [])

    def test_reused_pid_after_its_trace_observations_does_not_match(self):
        p = profile()
        p["trace_processor"]["pid_observations"][0]["last_seen"] = "12000000000"
        later = launch(run_id="reused-pid")
        later["observer"]["start_elapsed_ms"] = 17000
        later["observer"]["elapsed_ns"] = 18_000_000_000
        later["observer"]["wall_ms"] = 1_700_000_018_000
        result = process_context(p, launch()["observer"], [launch(), later])
        self.assertEqual([m["run_id"] for m in result["matches"]], ["source"])

    def test_missing_pid_observation_times_do_not_use_trace_bounds(self):
        for missing in (("first_seen",), ("last_seen",), ("first_seen", "last_seen")):
            p = profile()
            for field in missing:
                p["trace_processor"]["pid_observations"][0][field] = None
            with self.subTest(missing=missing):
                result = process_context(p, launch()["observer"], [launch()])
                self.assertEqual(result["matches"], [])

    def test_exit_before_profile_and_process_end_before_new_run_reject(self):
        source = launch()
        exit_row = {"event": "exit_info", "observer": source["observer"],
                    "details": {"source_pid": 42, "timestamp_ms": 1_700_000_007_000}}
        self.assertEqual(process_context(profile(), source["observer"], [source, exit_row])["matches"], [])
        p = profile()
        p["trace_processor"]["pid_observations"][0]["end_ts"] = "14000000000"
        source["observer"]["start_elapsed_ms"] = 15000
        source["observer"]["elapsed_ns"] = 16_000_000_000
        source["observer"]["wall_ms"] = 1_700_000_016_000
        self.assertEqual(process_context(p, source["observer"], [source])["matches"], [])

    def test_invalid_startup_snapshot_is_an_explicit_error(self):
        source = launch()
        source["observer"]["start_elapsed_ms"] = 11000
        with self.assertRaisesRegex(ValueError, "Invalid startup snapshot"):
            process_context(profile(), source["observer"], [source])

    def test_clock_tolerance_boundary_and_incomplete_evidence(self):
        source = launch()
        source["observer"]["elapsed_ns"] -= CLOCK_TOLERANCE_NS
        self.assertEqual(process_context(profile(), launch()["observer"], [source])["status"], "matched")
        source["observer"]["elapsed_ns"] -= 1
        self.assertEqual(process_context(profile(), launch()["observer"], [source])["matches"], [])
        p = profile()
        p["candidate_read_overflow"] = True
        self.assertEqual(process_context(p, launch()["observer"], [launch()])["status"], "incomplete_candidate_read")
        p = profile()
        p["trace_processor"]["clocks"] = []
        self.assertEqual(process_context(p, launch()["observer"], [launch()])["status"], "missing_clock_mapping")
        p = profile()
        p["trace_processor"]["clocks"][0]["max_offset_ns"] = str(1_700_000_000_000_000_000 + CLOCK_TOLERANCE_NS + 1)
        self.assertEqual(process_context(p, launch()["observer"], [launch()])["status"], "unstable_clock_mapping")
        observer = copy.deepcopy(launch()["observer"])
        del observer["installation_id"]
        self.assertEqual(process_context(profile(), observer, [launch()])["status"], "missing_installation_scope")

    def test_duplicates_preserve_deliveries_not_just_content(self):
        first = saved()
        second = saved(name="cccccccc-cccc-cccc-cccc-cccccccccccc.perfetto-trace")
        rows, files = deliveries([first, first, second])
        self.assertEqual(len(rows), 3)
        self.assertEqual(len(files), 2)
        self.assertEqual(rows[0]["observer"]["run_id"], "observer-not-source")

    def test_conflicting_file_identity_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            deliveries([saved(), saved(kind=7)])

    def test_bad_paths_hashes_and_sizes_are_rejected(self):
        for row in (
            saved(name="../aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.perfetto-trace"),
            saved(digest="not-a-digest"),
            saved(size=MAX_BYTES + 1),
            saved(size=0),
            saved(size=True),
        ):
            with self.subTest(row=row), self.assertRaises(ValueError):
                deliveries([row])

    def test_integrity_checks_actual_bytes(self):
        scratch = pathlib.Path(__file__).resolve().parents[4] / ".tmp"
        scratch.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            path = pathlib.Path(directory) / "trace"
            path.write_bytes(b"trace")
            digest = hashlib.sha256(b"trace").hexdigest()
            verify(path, digest, 5)
            with self.assertRaisesRegex(ValueError, "Size mismatch"):
                verify(path, digest, 4)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                verify(path, "a" * 64, 5)


if __name__ == "__main__":
    unittest.main()
