#!/bin/bash

set -euxo pipefail

# buildifier is used for format .bzl / BUILD / WORKSPACE files.
mkdir -p bin/
curl -LSs https://github.com/bazelbuild/buildtools/releases/download/v8.2.0/buildifier-linux-arm64 --output bin/buildifier
chmod +x bin/buildifier

curl -OL "https://github.com/tamasfe/taplo/releases/latest/download/taplo-linux-aarch64.gz"
gzip -d "taplo-linux-aarch64.gz"
mv "taplo-linux-aarch64" taplo
chmod +x ./taplo

curl -OL https://github.com/koalaman/shellcheck/releases/download/v0.10.0/shellcheck-v0.10.0.linux.aarch64.tar.xz
tar -xf shellcheck-v0.10.0.linux.aarch64.tar.xz
mv shellcheck-v0.10.0/shellcheck .
chmod +x ./shellcheck

# Brings in clang-format and uses clang for C++ compilation
sudo apt-get install -y clang
echo "CC=$(which clang)" >>"$GITHUB_ENV"
echo "CXX=$(which clang++)" >>"$GITHUB_ENV"
