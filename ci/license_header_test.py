import importlib.util
import pathlib
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name('license_header.py').resolve()
SCRATCH = SCRIPT.parent.parent / '.tmp'
SPEC = importlib.util.spec_from_file_location('license_header', SCRIPT)
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class LicenseHeaderTest(unittest.TestCase):
    def setUp(self):
        SCRATCH.mkdir(exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        subprocess.run(['git', 'init', '--quiet', str(self.root)], check=True)
        (self.root / 'LICENSE').write_bytes((SCRIPT.parent.parent / 'LICENSE').read_bytes())

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

    def test_linux_formatter_toolchains_are_ignored_by_repository_rules(self):
        (self.root / '.gitignore').write_bytes((SCRIPT.parent.parent / '.gitignore').read_bytes())
        toolchain_files = (
            'formatters/swift/usr/include/SourceKit/sourcekitd.h',
            'formatters/swift/usr/include/llvm-c/lto.h',
            'formatters/swift/usr/lib/clang/17/include/__clang_cuda_builtin_vars.h',
        )
        upstream = b'// Copyright upstream. Apache License 2.0.\nvoid entry();\n'
        for name in toolchain_files:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(upstream)
        result = self.run_checker('--check')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('formatters/', result.stdout)
        self.assertEqual(self.run_checker().returncode, 0)
        for name in toolchain_files:
            self.assertEqual((self.root / name).read_bytes(), upstream)

        source = self.root / 'formatters_extension/source.swift'
        source.parent.mkdir()
        source.write_text('body\n')
        result = self.run_checker('--check')
        self.assertEqual(result.returncode, 1)
        self.assertIn('formatters_extension/source.swift', result.stderr)
        self.assertNotIn('formatters/swift/', result.stderr)

    def test_cargo_license_metadata_is_required(self):
        (self.root / 'crate').mkdir()
        manifest = self.root / 'crate' / 'Cargo.toml'
        manifest.write_text('[package]\nname = "crate"\n')
        result = self.run_checker('--check')
        self.assertEqual(result.returncode, 1)
        self.assertIn('must declare only a license-file', result.stderr)
        manifest.write_text('[package]\nlicense-file = "../LICENSE"\n')
        self.assertEqual(self.run_checker('--check').returncode, 0)
        manifest.write_text('[package]\nlicense-file = "LICENSE"\n')
        self.assertEqual(self.run_checker('--check').returncode, 1)
        manifest.write_text('[package]\nlicense-file = "../LICENSE"\nlicense = "MIT"\n')
        self.assertEqual(self.run_checker('--check').returncode, 1)

    def test_canonical_header_normalization_preserves_notices(self):
        notice = '/* Copyright upstream. MIT License. */\nbody\n'
        notice = '\n'.join(CHECKER.header.lstrip().splitlines()[:2]) + '\n\n' + notice
        for extension in ('.rs', '.swift', '.kt', '.java', '.ts', '.js', '.h', '.c', '.m', '.mm'):
            path = self.root / ('source' + extension)
            original = CHECKER.header.lstrip() + '\n' + CHECKER.header.lstrip() + '\n' + notice
            path.write_text(original)
            self.assertEqual(self.run_checker('--check').returncode, 1)
            self.assertEqual(path.read_text(), original)
            self.assertEqual(self.run_checker().returncode, 0)
            expected = CHECKER.header.lstrip() + '\n' + notice
            self.assertEqual(path.read_text(), expected)
            self.assertEqual(self.run_checker().returncode, 0)
            self.assertEqual(path.read_text(), expected)
            self.assertEqual(self.run_checker('--check', str(path)).returncode, 0)
            path.unlink()

    def test_noncanonical_headers_are_rejected_without_writes(self):
        for name, preamble in (
            ('source.rs', ''),
            ('source.kt', ''),
            ('source.h', ''),
            ('ci/parse_benchmark_results.py', '#!/usr/bin/env python3\n'),
            ('platform/capture_flutter/ios/capture_flutter/Package.swift', '// swift-tools-version: 5.9\n\n'),
        ):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            header = CHECKER.header.lstrip()
            if path.suffix == '.py':
                header = '\n'.join(
                    line.replace('//', '#', 1) if line.startswith('//') else line
                    for line in header.split('\n'))
            incorrect = header.replace(CHECKER.STRICT_URL, 'https://example.invalid/obsolete-license')
            for original in (
                preamble + incorrect + '\nbody\n',
                preamble + header + '\n' + incorrect + '\nbody\n',
            ):
                for args in ((), ('--check',)):
                    with self.subTest(name=name, args=args, header=original):
                        path.write_text(original)
                        result = self.run_checker(*args, str(path))
                        self.assertEqual(result.returncode, 1)
                        self.assertIn('Unrecognized license header', result.stderr)
                        self.assertEqual(path.read_text(), original)
            path.unlink()

    def test_copied_core_headers_keep_their_canonical_license(self):
        header = CHECKER.header.lstrip().replace(
            "capture-sdk - bitdrift's client SDK",
            "shared-core - bitdrift's common client/server libraries").replace(
                '// LICENSE file', '// LICENSE.polyform file')
        for name in CHECKER.shared_header_files:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            original = header + '\nbody\n'
            path.write_text(original)
            self.assertEqual(self.run_checker('--check', str(path)).returncode, 0)
            self.assertEqual(self.run_checker(str(path)).returncode, 0)
            self.assertEqual(path.read_text(), original)
            for incorrect in (
                original.replace(CHECKER.STRICT_URL, 'https://example.invalid/obsolete-license'),
                original.replace('LICENSE.polyform', 'LICENSE'),
                CHECKER.header.lstrip() + '\nbody\n',
            ):
                path.write_text(incorrect)
                for args in ((), ('--check',)):
                    self.assertEqual(self.run_checker(*args, str(path)).returncode, 1)
                    self.assertEqual(path.read_text(), incorrect)

    def test_python_shebang_and_swift_tools_version_are_preserved(self):
        path = self.root / 'ci/parse_benchmark_results.py'
        path.parent.mkdir()
        header = CHECKER.header.lstrip()
        python_header = '\n'.join(
            line.replace('//', '#', 1) if line.startswith('//') else line
            for line in header.split('\n'))
        path.write_text('#!/usr/bin/env python3\n' + python_header + '\nprint("body")\n')
        package = self.root / 'platform/capture_flutter/ios/capture_flutter/Package.swift'
        package.parent.mkdir(parents=True)
        package.write_text('// swift-tools-version: 5.9\n\n' + header + '\nbody\n')
        self.assertEqual(self.run_checker().returncode, 0)
        self.assertTrue(path.read_text().startswith('#!/usr/bin/env python3\n# capture-sdk'))
        self.assertIn('# ' + CHECKER.STRICT_URL, path.read_text())
        self.assertTrue(package.read_text().startswith('// swift-tools-version: 5.9\n\n// capture-sdk'))
        self.assertEqual(self.run_checker('--check').returncode, 0)

    def test_invalid_license_text_fails(self):
        license_file = self.root / 'LICENSE'
        license_file.write_text(license_file.read_text() + '\nAdditional terms\n')
        self.assertEqual(self.run_checker('--check').returncode, 1)
        (self.root / 'LICENSE').write_text('not a license\n')
        self.assertEqual(self.run_checker('--check').returncode, 1)

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
