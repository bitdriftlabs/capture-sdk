import pathlib
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name('license_header.py').resolve()
SCRATCH = SCRIPT.parent.parent / '.tmp'


class LicenseHeaderTest(unittest.TestCase):
    def setUp(self):
        SCRATCH.mkdir(exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        subprocess.run(['git', 'init', '--quiet', str(self.root)], check=True)

    def run_checker(self, *args):
        return subprocess.run(
            ['python3', str(SCRIPT), *args], cwd=self.root,
            capture_output=True, text=True)

    def test_check_reports_all_missing_headers_without_modifying_files(self):
        for name in ('first.kt', 'second file.swift'):
            (self.root / name).write_text('original\n')
        result = self.run_checker('--check')
        self.assertEqual(result.returncode, 1)
        for name in ('first.kt', 'second file.swift'):
            self.assertIn(name, result.stderr)
            self.assertEqual((self.root / name).read_text(), 'original\n')

    def test_fix_then_check_accepts_uncommitted_files(self):
        (self.root / 'new.kt').write_text('original\n')
        self.assertEqual(self.run_checker().returncode, 0)
        self.assertEqual(self.run_checker('--check').returncode, 0)
        self.assertTrue((self.root / 'new.kt').read_text().endswith('original\n'))

    def test_ignored_output_is_excluded(self):
        (self.root / '.gitignore').write_text('build/\n')
        (self.root / 'build').mkdir()
        (self.root / 'build' / 'generated.kt').write_text('generated\n')
        self.assertEqual(self.run_checker('--check').returncode, 0)

    def test_cargo_license_metadata_is_required(self):
        (self.root / 'crate').mkdir()
        manifest = self.root / 'crate' / 'Cargo.toml'
        manifest.write_text('[package]\nname = "crate"\n')
        result = self.run_checker('--check')
        self.assertEqual(result.returncode, 1)
        self.assertIn('license-file = "LICENSE" not found', result.stderr)
        manifest.write_text('[package]\nlicense-file = "LICENSE"\n')
        self.assertEqual(self.run_checker('--check').returncode, 0)

    def test_explicit_root_manifest_and_swift_package_exemption(self):
        (self.root / 'Cargo.toml').write_text('[workspace]\n')
        package = self.root / 'platform/capture_flutter/ios/capture_flutter/Package.swift'
        package.parent.mkdir(parents=True)
        package.write_text('// swift-tools-version: 5.9\n')
        for manifest, swift_package in (
            ('Cargo.toml', str(package.relative_to(self.root))),
            (str(self.root / 'Cargo.toml'), str(package)),
        ):
            result = self.run_checker('--check', manifest, swift_package)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
