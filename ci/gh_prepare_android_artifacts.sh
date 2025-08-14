#!/bin/bash

set -euo pipefail

readonly version="$1"
repo="$(pwd)"
readonly sdk_repo="$repo"

function prepare_capture_sdk() {
  echo "+++ Preparing Android Capture SDK artifacts for '$version' version"

  pushd "$(mktemp -d)"
  local -r out_artifacts_dir="android-tmp"
  readonly dylibs_dir="capture-$version-dylib"

  mkdir "$out_artifacts_dir"
  mkdir "$dylibs_dir/"
  mkdir "$dylibs_dir/darwin_arm64/"
  mkdir "$dylibs_dir/darwin_x86_64/"

  echo "++ Unzipping Maven Android artifacts"

  unzip "$sdk_repo/Capture.android.zip" -d "$out_artifacts_dir"

  echo "++ Creating Android artifacts zip"

  (cd "$out_artifacts_dir" && zip -r "$sdk_repo/Capture-$version.android.zip" ./*)
  popd
}

prepare_capture_sdk
