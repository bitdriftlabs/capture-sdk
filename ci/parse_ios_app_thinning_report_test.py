#!/usr/bin/env python3

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from parse_ios_app_thinning_report import parse_app_size_kb


class ParseIosAppThinningReportTest(unittest.TestCase):
    def test_extracts_selected_model_size(self):
        report = """
App Thinning Size Report for All Variants of ExampleApp

Variant: ExampleApp.ipa
Supported variant descriptors: [device: iPhone18,3, os-version: 26.0]
App + On Demand Resources size: 8.2 MB compressed, 21.6 MB uncompressed
App size: 7.7 MB compressed, 20.1 MB uncompressed
On Demand Resources size: Zero KB compressed, Zero KB uncompressed
"""

        self.assertEqual(parse_app_size_kb(report, "iPhone18,3"), 7885)

    def test_rejects_missing_model_rows(self):
        report = """
App Thinning Size Report for All Variants of ExampleApp

Variant: ExampleApp.ipa
Supported variant descriptors: [device: iPhone17,1, os-version: 18.0]
App size: 5.4 MB compressed, 13.7 MB uncompressed
"""

        with self.assertRaisesRegex(ValueError, "iPhone18,3"):
            parse_app_size_kb(report, "iPhone18,3")

    def test_handles_comma_and_space_formatted_sizes(self):
        comma_report = """
Variant: ExampleApp.ipa
Supported variant descriptors: [device: iPhone18,3, os-version: 26.0]
App size: 1,234 KB compressed, 2,468 KB uncompressed
"""
        space_report = """
Variant: ExampleApp.ipa
Supported variant descriptors: [device: iPhone18,3, os-version: 26.0]
App size: 1 234 KB compressed, 2 468 KB uncompressed
"""

        self.assertEqual(parse_app_size_kb(comma_report, "iPhone18,3"), 1234)
        self.assertEqual(parse_app_size_kb(space_report, "iPhone18,3"), 1234)


if __name__ == "__main__":
    unittest.main()
