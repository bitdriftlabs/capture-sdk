# Insert license headers on files, running over all of particular types in the
# current directory if no particular files specified
import argparse
import os
import subprocess
import sys

# Define the header you want to check for and insert
header = """
// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
"""

headers = {
    '.rs': header,
    '.kt': header,
    '.java': header,
    '.swift': header,
}

# TODO(mattklein123): Figure out if we want our license at the top of the generated proto files.
# If so we need to build it into the generator.
exclude_dirs = (
    './.git',
    './target/',
)
exclude_files = (
    # Swift Package Manager requires swift-tools-version to be the first line.
    './platform/capture_flutter/ios/capture_flutter/Package.swift',
)

extensions_to_check = ('.rs', '.toml', '.kt', '.java', '.swift', '.js', '.ts')


def check_file(file_path, check=False):
    file_path = './' + os.path.relpath(file_path)
    for dir in exclude_dirs:
        if file_path.startswith(dir):
            return True

    if file_path in exclude_files:
        return True

    _, ext = os.path.splitext(file_path)
    if not ext in extensions_to_check:
        return True

    print(f'Checking {file_path}')
    with open(file_path, 'r') as file:
        content = file.read()

    if (file_path.endswith('Cargo.toml') and
        not file_path == './Cargo.toml' and
            not 'license-file = "LICENSE"' in content):
        print(f'license-file = "LICENSE" not found in {file_path}', file=sys.stderr)
        return False

    header = headers.get(ext)
    if not header:
        return True
    header = header.lstrip()

    if not content.startswith(header):
        if check:
            print(f'Missing license header: {file_path}', file=sys.stderr)
            return False
        with open(file_path, 'w') as file:
            file.write(header + '\n' + content)
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
    for path in args.files or iterate_over_files():
        valid = check_file(path, check=args.check) and valid
    sys.exit(0 if valid else 1)
