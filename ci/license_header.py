# Insert license headers on files, running over all of particular types in the
# current directory if no particular files specified
import argparse
import hashlib
import os
import re
import subprocess
import sys
import tomllib
from pathlib import Path

STRICT_URL = 'https://polyformproject.org/licenses/strict/1.0.0'
STRICT_LICENSE_SHA256 = '90f9e6b7d79f381706abd79e5302039ca09856922a2cdc9bc91087fd1535581e'

# Define the header you want to check for and insert
header = """
// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0
"""

shared_header_files = {
    './platform/swift/source/crash_handler/bd-bonjson/ffi.h',
    './platform/swift/source/reports/bd-report-writer/ffi.h',
}

# TODO(mattklein123): Figure out if we want our license at the top of the generated proto files.
# If so we need to build it into the generator.
exclude_dirs = (
    './.git',
    './target/',
)
optional_header_files = (
    # Swift Package Manager requires swift-tools-version to be the first line.
    './platform/capture_flutter/ios/capture_flutter/Package.swift',
)

extensions_to_check = (
    '.rs', '.kt', '.java', '.swift', '.js', '.ts', '.h', '.c', '.cc', '.cpp', '.m', '.mm',
)

extra_header_files = {
    './ci/parse_benchmark_results.py',
    './ci/parse_benchmark_results_test.py',
}


def check_file(file_path, check=False):
    file_path = './' + os.path.relpath(file_path)
    for dir in exclude_dirs:
        if file_path.startswith(dir):
            return True

    _, ext = os.path.splitext(file_path)
    is_manifest = Path(file_path).name == 'Cargo.toml'
    if not (ext in extensions_to_check or is_manifest or file_path in extra_header_files):
        return True

    print(f'Checking {file_path}')
    with open(file_path, 'r') as file:
        content = file.read()

    if is_manifest:
        package = tomllib.loads(content).get('package')
        if package is not None:
            license_file = package.get('license-file')
            if not isinstance(license_file, str) or 'license' in package:
                print(f'{file_path} must declare only a license-file pointing to root LICENSE', file=sys.stderr)
                return False
            resolved = (Path(file_path).parent / license_file).resolve()
            if resolved != Path('LICENSE').resolve() or not resolved.is_file():
                print(f'{file_path} license-file must resolve to the root LICENSE', file=sys.stderr)
                return False
        return True

    preamble = ''
    body = content
    if (ext == '.py' and body.startswith('#!')) or body.startswith('// swift-tools-version:'):
        first_line, _, body = body.partition('\n')
        preamble = first_line + '\n'
        if body.startswith('\n'):
            preamble += '\n'
            body = body[1:]
    comment = '#' if ext == '.py' else '//'
    expected_header = re.sub(r'^//', comment, header.lstrip(), flags=re.MULTILINE)
    if file_path in shared_header_files:
        expected_header = expected_header.replace(
            "capture-sdk - bitdrift's client SDK",
            "shared-core - bitdrift's common client/server libraries").replace(
                '// LICENSE file', '// LICENSE.polyform file')
    for banner in (
        f"{comment} capture-sdk - bitdrift's client SDK\n",
        f"{comment} shared-core - bitdrift's common client/server libraries\n",
    ):
        for match in re.finditer(re.escape(banner), content):
            block = content[match.start():].partition('\n\n')[0]
            if not content.startswith(expected_header, match.start()) and re.search(
                rf'^{re.escape(comment)}[^\n]*licens', block, flags=re.MULTILINE | re.IGNORECASE,
            ):
                print(f'Unrecognized license header: {file_path}', file=sys.stderr)
                return False
    if file_path in optional_header_files and not body.startswith(expected_header):
        return True
    while body.startswith(expected_header):
        body = body[len(expected_header):].lstrip('\n')
    expected_content = preamble + expected_header + '\n' + body
    if content != expected_content:
        if check:
            print(f'Missing or incorrect license header: {file_path}', file=sys.stderr)
            return False
        with open(file_path, 'w') as file:
            file.write(expected_content)
    return True


def iterate_over_files():
    files = subprocess.check_output(
        ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'])
    for file_path in sorted(set(os.fsdecode(files).split('\0')) - {''}):
        if os.path.isfile(file_path):
            yield file_path


# Run the script
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='Check without modifying files')
    parser.add_argument('files', nargs='*')
    args = parser.parse_args()
    valid = True
    try:
        license_text = Path('LICENSE').read_bytes()
        if hashlib.sha256(license_text).hexdigest() != STRICT_LICENSE_SHA256:
            raise RuntimeError('LICENSE must contain PolyForm Strict License 1.0.0')
        for path in args.files or iterate_over_files():
            valid = check_file(path, check=args.check) and valid
    except (OSError, RuntimeError, tomllib.TOMLDecodeError) as error:
        print(error, file=sys.stderr)
        valid = False
    sys.exit(0 if valid else 1)
