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

function prepare_capture_timber() {
  echo "+++ Preparing Android Capture Timber library artifacts for '$version' version"

  cp "$sdk_repo/capture-timber.zip" "$sdk_repo/capture-timber-$version.android.zip"
}

function prepare_capture_apollo3() {
  echo "+++ Preparing Android Capture Apollo3 library artifacts for '$version' version"

  cp "$sdk_repo/capture-apollo3.zip" "$sdk_repo/capture-apollo4-$version.android.zip"
}

function prepare_capture_plugin() {
  echo "+++ Preparing Android Capture Android Plugin artifacts for '$version' version"

  cp "$sdk_repo/capture-plugin.zip" "$sdk_repo/capture-plugin-$version.android.zip"
}

prepare_capture_sdk
prepare_capture_timber
prepare_capture_plugin
