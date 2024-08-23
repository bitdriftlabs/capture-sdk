#!/bin/bash

set -euo pipefail

# Downloads and extracts the prebuilt llvm packages for use in CI.
readonly llvm_version=14.0.0

curl -L "https://github.com/llvm/llvm-project/releases/download/llvmorg-$llvm_version/clang+llvm-$llvm_version-x86_64-linux-gnu-ubuntu-18.04.tar.xz" -o llvm.tar.xz
mkdir -p llvm/
pushd llvm/
  tar xfv ../llvm.tar.xz
popd
