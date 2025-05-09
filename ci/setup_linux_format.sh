#!/bin/bash

set -euxo pipefail

# buildifier is used for format .bzl / BUILD / WORKSPACE files.
mkdir -p bin/
echo "Downloading buildifier..."
download_with_timing() {
  local description=$1
  local url=$2
  local output=$3

  echo "Downloading ${description}..."
  start_time=$(date +%s)
  curl -LSs "${url}" --output "${output}"
  end_time=$(date +%s)
  download_time=$((end_time - start_time))
  echo "${description} download completed in ${download_time} seconds"
}

download_with_timing "buildifier" "https://github.com/bazelbuild/buildtools/releases/download/v8.2.0/buildifier-linux-arm64" "bin/buildifier"
chmod +x bin/buildifier

download_with_timing "taplo" "https://github.com/tamasfe/taplo/releases/latest/download/taplo-linux-aarch64.gz" "taplo-linux-aarch64.gz"
gzip -d "taplo-linux-aarch64.gz"
mv "taplo-linux-aarch64" taplo
chmod +x ./taplo

download_with_timing "shellcheck" "https://github.com/koalaman/shellcheck/releases/download/v0.10.0/shellcheck-v0.10.0.linux.aarch64.tar.xz" "shellcheck-v0.10.0.linux.aarch64.tar.xz"
tar -xf shellcheck-v0.10.0.linux.aarch64.tar.xz
mv shellcheck-v0.10.0/shellcheck .
chmod +x ./shellcheck

# Brings in clang-format and uses clang for C++ compilation
sudo apt-get install -y clang
echo "CC=$(which clang)" >>"$GITHUB_ENV"
echo "CXX=$(which clang++)" >>"$GITHUB_ENV"
