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

# Optional second argument: custom artifact name (e.g. "capture-no-jank-stats")
# Defaults to "capture" if not provided.
readonly artifact_name="${2:-capture}"

./bazelw build \
  --announce_rc \
  --config=ci \
  --config=release-android \
  --define=pom_version="$version" \
  //:capture_aar_with_artifacts //:capture_symbols

mkdir -p dist/

sdk_repo="$(pwd)"
pushd "$(mktemp -d)"
  mv "$sdk_repo/bazel-bin/capture.aar" "$artifact_name-$version.aar"
  mv "$sdk_repo/bazel-bin/capture.pom" "$artifact_name-$version.pom"
  mv "$sdk_repo/bazel-bin/capture-sources.jar" "$artifact_name-$version-sources.jar"
  mv "$sdk_repo/bazel-bin/capture-javadoc.jar" "$artifact_name-$version-javadoc.jar"
  mv "$sdk_repo/bazel-bin/symbols.tar" "$artifact_name-$version-symbols.tar"
  cp "$sdk_repo/ci/LICENSE" "LICENSE"

  # If using a custom artifact name, update artifact-specific fields in the POM
  if [ "$artifact_name" != "capture" ]; then
    sed -i \
      -e "s|<artifactId>capture</artifactId>|<artifactId>$artifact_name</artifactId>|" \
      -e "s|/capture/|/$artifact_name/|g" \
      "$artifact_name-$version.pom"
  fi

  zip -j -r "$sdk_repo/dist/Capture.android.zip" ./*
popd
