#!/usr/bin/env python3

import argparse
import re
import sys
import tempfile
import zipfile
from contextlib import contextmanager
from pathlib import Path

# Everything we assert lives in the two comment lines the compiler writes at the top of every
# .swiftinterface. For example:
#
# // swift-compiler-version: Apple Swift version 6.4 effective-5.10 (swiftlang-6.4.0.20.104 ...)
# // swift-module-flags: -target arm64-apple-ios15.0 -enable-library-evolution -language-mode 5 ...
#
# We also check that the structure makes sense (e.g. it has the expected slices, it has .swiftinterfaces, etc.)
MODULE_NAME = "Capture"
XCFRAMEWORK_NAME = f"{MODULE_NAME}.xcframework"
DEFAULT_LANGUAGE_MODE = "5"

EXPECTED_SLICES = ("ios-arm64", "ios-arm64_x86_64-simulator")
EXPECTED_INTERFACES = (
    ("ios-arm64", "arm64"),
    ("ios-arm64_x86_64-simulator", "arm64"),
    ("ios-arm64_x86_64-simulator", "x86_64"),
)

COMPILER_LINE_RE = re.compile(
    r"^// swift-compiler-version:.*Apple Swift version (?P<version>[\d.]+) "
    r"effective-(?P<language_mode>[\d.]+)"
)
FLAGS_LINE_PREFIX = "// swift-module-flags:"
MINIMUM_OS_RE = re.compile(
    r"^build --ios_minimum_os=(?P<version>[\d.]+)$", re.MULTILINE
)


def parse_module_flags(line):
    """Turn a swift-module-flags line into {flag: value or None}."""
    flags = {}
    tokens = line[len(FLAGS_LINE_PREFIX) :].split()
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if not token.startswith("-"):
            index += 1
            continue
        value = None
        if index + 1 < len(tokens) and not tokens[index + 1].startswith("-"):
            value = tokens[index + 1]
            index += 1
        flags[token] = value
        index += 1
    return flags


class ModuleInterface:
    def __init__(self, slice_id, arch, compiler_version, language_mode, flags):
        self.slice_id = slice_id
        self.arch = arch
        self.compiler_version = compiler_version
        self.language_mode = language_mode
        self.flags = flags

    @classmethod
    def parse(cls, slice_id, arch, header):
        compiler_version = None
        language_mode = None
        flags = {}
        for line in header.splitlines():
            match = COMPILER_LINE_RE.match(line)
            if match:
                compiler_version = match.group("version")
                language_mode = match.group("language_mode")
            elif line.startswith(FLAGS_LINE_PREFIX):
                flags = parse_module_flags(line)
        return cls(slice_id, arch, compiler_version, language_mode, flags)

    @property
    def is_simulator(self):
        return self.slice_id.endswith("-simulator")

    def __str__(self):
        return f"{self.slice_id}/{self.arch}"


class Framework:
    def __init__(self, slices, interfaces):
        self.slices = slices
        self.interfaces = interfaces

    @classmethod
    def load(cls, root):
        slices = sorted(path.name for path in root.iterdir() if path.is_dir())
        interfaces = {}
        for slice_id, arch in EXPECTED_INTERFACES:
            path = (
                root
                / slice_id
                / f"{MODULE_NAME}.framework"
                / "Modules"
                / f"{MODULE_NAME}.swiftmodule"
                / f"{arch}.swiftinterface"
            )
            if path.is_file():
                interfaces[(slice_id, arch)] = ModuleInterface.parse(
                    slice_id, arch, path.read_text()
                )
            else:
                interfaces[(slice_id, arch)] = None
        return cls(slices, interfaces)

    def present_interfaces(self):
        return [
            interface for interface in self.interfaces.values() if interface is not None
        ]


class Expectations:
    def __init__(
        self, toolchain_version, minimum_os, language_mode=DEFAULT_LANGUAGE_MODE
    ):
        self.toolchain_version = toolchain_version
        self.minimum_os = minimum_os
        self.language_mode = language_mode


def _matches_version(actual, expected):
    return actual == expected or (actual or "").startswith(f"{expected}.")


def check_every_expected_slice_is_present(framework, expected):
    return [
        f"missing slice: {slice_id}"
        for slice_id in EXPECTED_SLICES
        if slice_id not in framework.slices
    ]


def check_every_slice_exposes_a_module_interface(framework, expected):
    return [
        f"missing interface: {slice_id}/{arch}"
        for (slice_id, arch), interface in framework.interfaces.items()
        if interface is None
    ]


def check_built_with_the_expected_toolchain(framework, expected):
    return [
        f"{interface}: built with Swift {interface.compiler_version}"
        for interface in framework.present_interfaces()
        if not _matches_version(interface.compiler_version, expected.toolchain_version)
    ]


def check_effective_language_mode_is_expected(framework, expected):
    return [
        f"{interface}: effective language mode is {interface.language_mode}"
        for interface in framework.present_interfaces()
        if not _matches_version(interface.language_mode, expected.language_mode)
    ]


def check_language_mode_is_pinned_explicitly(framework, expected):
    failures = []
    for interface in framework.present_interfaces():
        # Swift 6.2 records the pin as -swift-version, Swift 6.4 as -language-mode.
        mode = interface.flags.get("-language-mode") or interface.flags.get(
            "-swift-version"
        )
        if mode is None:
            failures.append(f"{interface}: no explicit language mode pin")
        elif mode != expected.language_mode:
            failures.append(f"{interface}: pinned to language mode {mode}")
    return failures


def check_library_evolution_is_enabled(framework, expected):
    return [
        f"{interface}: built without -enable-library-evolution"
        for interface in framework.present_interfaces()
        if "-enable-library-evolution" not in interface.flags
    ]


def check_deployment_target_matches_bazelrc(framework, expected):
    failures = []
    for interface in framework.present_interfaces():
        target = f"{interface.arch}-apple-ios{expected.minimum_os}"
        if interface.is_simulator:
            target += "-simulator"
        if interface.flags.get("-target") != target:
            failures.append(
                f"{interface}: targets {interface.flags.get('-target')}, expected {target}"
            )
    return failures


def check_module_is_named_capture(framework, expected):
    return [
        f"{interface}: exposes module {interface.flags.get('-module-name')}"
        for interface in framework.present_interfaces()
        if interface.flags.get("-module-name") != MODULE_NAME
    ]


CHECKS = (
    check_every_expected_slice_is_present,
    check_every_slice_exposes_a_module_interface,
    check_built_with_the_expected_toolchain,
    check_effective_language_mode_is_expected,
    check_language_mode_is_pinned_explicitly,
    check_library_evolution_is_enabled,
    check_deployment_target_matches_bazelrc,
    check_module_is_named_capture,
)


def read_minimum_os(bazelrc):
    match = MINIMUM_OS_RE.search(bazelrc.read_text())
    if not match:
        raise SystemExit(f"could not read --ios_minimum_os from {bazelrc}")
    return match.group("version")


@contextmanager
def framework_root(artifact):
    if artifact.is_dir():
        yield artifact
        return
    with tempfile.TemporaryDirectory() as work:
        with zipfile.ZipFile(artifact) as archive:
            archive.extractall(work)
        root = Path(work) / XCFRAMEWORK_NAME
        if not root.is_dir():
            raise SystemExit(f"no {XCFRAMEWORK_NAME} found inside {artifact}")
        yield root


def run_checks(framework, expected, out=sys.stdout):
    failed = 0
    for check in CHECKS:
        failures = check(framework, expected)
        name = check.__name__[len("check_") :]
        if failures:
            failed += 1
            print(f"  FAIL {name}", file=out)
            for failure in failures:
                print(f"       {failure}", file=out)
        else:
            print(f"  ok   {name}", file=out)
    return failed


def main():
    parser = argparse.ArgumentParser(
        description="Run some validation tests on the generated .xcframework"
    )
    parser.add_argument(
        "artifact", type=Path, help="Capture.zip or Capture.xcframework"
    )
    parser.add_argument(
        "toolchain_version",
        help="Swift toolchain the artifact must be built with, e.g. 6.2",
    )
    parser.add_argument(
        "--language-mode",
        default=DEFAULT_LANGUAGE_MODE,
        help=f"Swift language mode the artifact must expose (default: {DEFAULT_LANGUAGE_MODE})",
    )
    args = parser.parse_args()

    expected = Expectations(
        args.toolchain_version,
        read_minimum_os(Path(__file__).resolve().parent.parent / ".bazelrc"),
        args.language_mode,
    )

    print(
        f"Checking {args.artifact} against Swift {args.toolchain_version}, "
        f"language mode {args.language_mode}"
    )
    with framework_root(args.artifact) as root:
        framework = Framework.load(root)

    failed = run_checks(framework, expected)
    if failed:
        sys.stdout.flush()
        print(f"{failed} check(s) failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
