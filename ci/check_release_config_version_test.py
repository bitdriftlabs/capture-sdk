import unittest

from check_release_config_version import sdk_minor, validate


class ReleaseConfigVersionTest(unittest.TestCase):
    def test_semver_minor(self):
        for version in ["0.49.0", "0.49.7", "1.49.0", "0.49.0-rc.1", "0.49.0+build.5"]:
            with self.subTest(version=version):
                self.assertEqual(sdk_minor(version), 49)
        for version in ["v0.49.0", "0.49", "0.049.0", "0.49.0-01", "0.49.0-", "0.49.0\n"]:
            with self.subTest(version=version), self.assertRaises(ValueError):
                sdk_minor(version)

    def test_matching_minor(self):
        for version in ["0.49.0", "0.49.7", "1.49.0", "0.49.0-rc.1", "0.49.0+build.5"]:
            with self.subTest(version=version):
                validate(version, "49")

    def test_mismatch_and_invalid_output(self):
        for version in ["0.48.0", "0.50.0", "1.0.0"]:
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, "config version is 49"):
                validate(version, "49")
        for output in ["", "0", "049", "unknown", "49\n", "49\n50", "config: 49"]:
            with self.subTest(output=output), self.assertRaisesRegex(ValueError, "Invalid config-version"):
                validate("0.49.0", output)


if __name__ == "__main__":
    unittest.main()
