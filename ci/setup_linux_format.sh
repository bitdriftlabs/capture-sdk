#!/bin/bash

set -euxo pipefail

# buildifier is used for format .bzl / BUILD / WORKSPACE files.
mkdir -p bin/
curl -LSs https://github.com/bazelbuild/buildtools/releases/download/6.0.1/buildifier-linux-amd64 --output bin/buildifier
chmod +x bin/buildifier

# The binaries above dynamically link a library provided by Swift, so download Swift + update the
# LD_LIBRARY_PATH tell the system how to find them.
swift_archive_name="swift-5.7.3-RELEASE-ubuntu22.04"
curl -OL "https://download.swift.org/swift-5.7.3-release/ubuntu2204/swift-5.7.3-RELEASE/$swift_archive_name.tar.gz"
tar xf "$swift_archive_name.tar.gz"

# swiftlint provides static linting of Swif code.
curl -OL https://github.com/realm/SwiftLint/releases/download/0.57.0/swiftlint_linux.zip --output bin/swiftlint
unzip bin/swiftlint/swiftlint_linux.zip
mv bin/swiftlint/swiftlint ./swiftlint
chmod +x ./swiftlint

curl -OL "https://github.com/tamasfe/taplo/releases/download/0.8.1/taplo-linux-x86_64.gz"
gzip -d "taplo-linux-x86_64.gz"
mv "taplo-linux-x86_64" taplo
chmod +x ./taplo

echo "PATH=$(pwd):$(pwd)/$swift_archive_name/usr/bin:$PATH" >> "$GITHUB_ENV"
echo "LD_LIBRARY_PATH=$(pwd)/$swift_archive_name/usr/lib" >> "@GITHUB_ENV"

# Brings in clang-format and uses clang for C++ compilation
sudo apt-get install -y clang
echo "CC=$(which clang)" >> "$GITHUB_ENV"
echo "CXX=$(which clang++)" >> "$GITHUB_ENV"
