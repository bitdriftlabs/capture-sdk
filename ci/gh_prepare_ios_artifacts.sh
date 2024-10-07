#!/bin/bash

set -euo pipefail

readonly version="$1"

sdk_repo="$(pwd)"
readonly sdk_repo="$sdk_repo"

function prepare_library_artifacts() {
  local -r library="$1"

  echo "+++ Preparing $library.ios.zip"

  pushd "$(mktemp -d)"
    unzip -o "$sdk_repo/$library.ios.zip" -d tmp
    unzip -o "tmp/$library.xcframework.zip" -d tmp
    rm -rf "tmp/$library.xcframework.zip"

    (cd tmp && zip -r "$sdk_repo/$library-$version.ios.zip" ./*)
  popd
}

prepare_library_artifacts "Capture"
