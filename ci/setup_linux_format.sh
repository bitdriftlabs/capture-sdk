#!/bin/bash

set -euxo pipefail

# buildifier is used for format .bzl / BUILD / WORKSPACE files.
mkdir -p formatters/
readonly formatter_dir
formatter_dir="$(pwd)/formatters"

curl -LSs https://github.com/bazelbuild/buildtools/releases/download/6.0.1/buildifier-linux-amd64 --output "$formatter_dir/buildifier"
chmod +x "$formatter_dir/buildifier"

# The binaries above dynamically link a library provided by Swift, so download Swift + update the
# LD_LIBRARY_PATH tell the system how to find them.
swift_archive_name="swift-5.7.3-RELEASE-ubuntu22.04"
curl -OL "https://download.swift.org/swift-5.7.3-release/ubuntu2204/swift-5.7.3-RELEASE/$swift_archive_name.tar.gz"
tar xf "$swift_archive_name.tar.gz"
mv "$swift_archive_name" "$formatter_dir/swift"

# swiftlint provides static linting of Swift code.
pushd "$(mktemp -d)"
curl -OL https://github.com/realm/SwiftLint/releases/download/0.57.0/swiftlint_linux.zip
unzip swiftlint_linux.zip
mv swiftlint "$formatter_dir/swiftlint"
chmod +x "$formatter_dir/swiftlint"
popd

curl -OL "https://github.com/tamasfe/taplo/releases/download/0.8.1/taplo-linux-x86_64.gz"
gzip -d "taplo-linux-x86_64.gz"
mv "taplo-linux-x86_64" "$formatter_dir/taplo"
chmod +x ./taplo

pushd "$(mktemp -d)"
scversion="stable"
wget -qO- "https://github.com/koalaman/shellcheck/releases/download/${scversion?}/shellcheck-${scversion?}.linux.x86_64.tar.xz" | tar -xJv
mv "shellcheck-${scversion}/shellcheck" "$formatter_dir/shellcheck"
"$formatter_dir/shellcheck" --version
popd
