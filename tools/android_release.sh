#!/bin/bash

set -euo pipefail

if [ $# -eq 0 ]
  then
    # Use commit SHA as version if version argument was not passed.
    version="$(git rev-parse --short HEAD)"
    readonly version
  else
    readonly version="$1"
fi

./bazelw build \
  --announce_rc \
  --config=ci \
  --config=release-android \
  --define=pom_version="$version" \
  --embed_label "$version" \
  //:capture_aar_with_artifacts //:capture_symbols

mkdir -p dist/

sdk_repo="$(pwd)"
pushd "$(mktemp -d)"
  mv "$sdk_repo/bazel-bin/capture.aar" "capture-$version.aar"
  mv "$sdk_repo/bazel-bin/capture.pom" "capture-$version.pom"
  mv "$sdk_repo/bazel-bin/capture-sources.jar" "capture-$version-sources.jar"
  mv "$sdk_repo/bazel-bin/capture-javadoc.jar" "capture-$version-javadoc.jar"
  mv "$sdk_repo/bazel-bin/symbols.tar" "capture-$version-symbols.tar"
  cp "$sdk_repo/ci/LICENSE.txt" "LICENSE.txt"
  cp "$sdk_repo/ci/NOTICE.txt" "NOTICE.txt"

  zip -j -r "$sdk_repo/dist/Capture.android.zip" ./*
popd
