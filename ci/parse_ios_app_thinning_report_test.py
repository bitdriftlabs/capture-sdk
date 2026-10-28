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
    def test_distinct_variants_require_a_device_model(self):
        with self.assertRaisesRegex(ValueError, 'pass a device model'):
            parse_app_size_kb(REPORT)

    def test_explicit_device_selects_its_compressed_size(self):
        self.assertEqual(parse_app_size_kb(REPORT, 'iPhone18,1'), 8397)
        self.assertEqual(parse_app_size_kb(REPORT, 'iPhone18,3'), 7885)

    def test_single_exported_variant_matches_full_report_selection(self):
        thinned_report = REPORT[REPORT.index('Variant: ExampleApp-iPhone18,3.ipa'):]
        self.assertEqual(
            parse_app_size_kb(thinned_report, 'iPhone18,3'),
            parse_app_size_kb(REPORT, 'iPhone18,3'),
        )

    def test_shared_variant_sizes_remain_valid_without_a_model(self):
        report = REPORT.replace('8.2 MB compressed', '7.7 MB compressed')
        self.assertEqual(parse_app_size_kb(report), 7885)


if __name__ == '__main__':
    unittest.main()
