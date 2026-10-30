import unittest

from parse_ios_app_thinning_report import parse_app_size_kb


REPORT = """Variant: ExampleApp-iPhone18,1.ipa
Supported variant descriptors: [device: iPhone18,1, os-version: 26.0]
App size: 8.2 MB compressed, 21.6 MB uncompressed

Variant: ExampleApp-iPhone18,3.ipa
Supported variant descriptors: [device: iPhone18,3, os-version: 26.0]
App size: 7.7 MB compressed, 20.1 MB uncompressed
"""


class AppThinningReportTest(unittest.TestCase):
    def test_without_model_uses_largest_compressed_size_in_any_order(self):
        self.assertEqual(parse_app_size_kb(REPORT), 8397)
        reversed_report = "\n\n".join(reversed(REPORT.split("\n\n")))
        self.assertEqual(parse_app_size_kb(reversed_report), 8397)

    def test_explicit_device_selects_its_compressed_size(self):
        self.assertEqual(parse_app_size_kb(REPORT, "iPhone18,1"), 8397)
        self.assertEqual(parse_app_size_kb(REPORT, "iPhone18,3"), 7885)

    def test_single_exported_variant_matches_full_report_selection(self):
        thinned_report = REPORT[REPORT.index("Variant: ExampleApp-iPhone18,3.ipa"):]
        self.assertEqual(parse_app_size_kb(thinned_report), 7885)
        self.assertEqual(parse_app_size_kb(thinned_report, "iPhone18,3"), 7885)

    def test_shared_variant_sizes_remain_valid_without_a_model(self):
        report = REPORT.replace("8.2 MB compressed", "7.7 MB compressed")
        self.assertEqual(parse_app_size_kb(report), 7885)

    def test_largest_size_compares_units_and_compressed_values(self):
        report = REPORT.replace("8.2 MB compressed", "900 KB compressed")
        self.assertEqual(parse_app_size_kb(report), 7885)

    def test_zero_size_is_valid(self):
        self.assertEqual(parse_app_size_kb("Variant: Empty.ipa\nApp size: Zero KB compressed,"), 0)

    def test_missing_sizes_fail(self):
        for report in ("", "Variant: Empty.ipa\nNo size available"):
            with self.subTest(report=report):
                with self.assertRaisesRegex(ValueError, "no compressed app size"):
                    parse_app_size_kb(report)

    def test_unknown_model_fails_even_when_all_sizes_are_equal(self):
        report = REPORT.replace("8.2 MB compressed", "7.7 MB compressed")
        with self.assertRaisesRegex(ValueError, "no app thinning report variant"):
            parse_app_size_kb(report, "iPhone99,1")


if __name__ == "__main__":
    unittest.main()
