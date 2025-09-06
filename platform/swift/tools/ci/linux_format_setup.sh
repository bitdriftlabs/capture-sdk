#!/bin/bash

set -euxo pipefail

mkdir -p bin/

# swiflint - Swift code formatting & linting.
curl -OL https://github.com/realm/SwiftLint/releases/download/0.54.0/swiftlint_linux.zip
unzip swiftlint_linux.zip
mv swiftlint bin/
chmod +x bin/swiftlint

# SwiftFormat - More advanced Swift code formatting.
curl -OL https://github.com/nicklockwood/SwiftFormat/releases/download/0.53.5/swiftformat_linux.zip
unzip swiftformat_linux.zip
mv swiftformat_linux bin/swiftformat
chmod +x bin/swiftformat

# DrString - Validate documentation comments.
curl -OL https://github.com/dduan/DrString/releases/download/0.6.1/drstring-x86_64-unknown-ubuntu.tar.gz
mkdir -p drstring-x86_64-unknown-ubuntu/
tar xf drstring-x86_64-unknown-ubuntu.tar.gz -C drstring-x86_64-unknown-ubuntu
mv drstring-x86_64-unknown-ubuntu/usr/bin/drstring bin/drstring
chmod +x bin/drstring

# The binaries above dynamically link a library provided by Swift, so download Swift + update the
# LD_LIBRARY_PATH tell the system how to find them.
swift_archive_name="swift-5.9.2-RELEASE-ubuntu22.04"
curl -OL "https://download.swift.org/swift-5.9.2-release/ubuntu2204/swift-5.9.2-RELEASE/$swift_archive_name.tar.gz"
tar xf "$swift_archive_name.tar.gz"

echo "PATH=$(pwd):$(pwd)/$swift_archive_name/usr/bin:$PATH" >> "$GITHUB_ENV"
echo "LD_LIBRARY_PATH=$(pwd)/$swift_archive_name/usr/lib" >> "@GITHUB_ENV"
