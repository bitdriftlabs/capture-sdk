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

    echo "++ Creating shared dylibs"

    mv "$sdk_repo/libcapture.dylib.arm64" "$dylibs_dir/darwin_arm64/libcapture.dylib"
    mv "$sdk_repo/libcapture.dylib.x86_64" "$dylibs_dir/darwin_x86_64/libcapture.dylib"
    mv "$sdk_repo/libcapture.so" "$dylibs_dir/"

    cp "$sdk_repo/ci/BUILD.shared_libs" "$dylibs_dir"/BUILD

    pushd "$dylibs_dir"
      tar cvf "../$out_artifacts_dir/$dylibs_dir.tar" ./*
    popd

    echo "++ Unzipping Maven Android artifacts"

    unzip "$sdk_repo/Capture.android.zip" -d "$out_artifacts_dir"

    echo "++ Creating Android artifacts zip"

    (cd "$out_artifacts_dir" && zip -r "$sdk_repo/Capture-$version.android.zip" ./*)
  popd
}

prepare_capture_sdk
