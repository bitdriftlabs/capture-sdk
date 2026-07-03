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

# Optional third argument: include Google Play SDK verification file in the AAR.
# Defaults to false for normal releases.
readonly include_sdk_verification_file="${3:-false}"

readonly sdk_verification_path="META-INF/io/bitdrift/capture/verification.properties"

function verify_sdk_verification_file() {
  local -r aar_path="$1"
  local -r temp_dir="$(mktemp -d)"

  unzip -q "$aar_path" classes.jar -d "$temp_dir"

  if ! unzip -Z1 "$temp_dir/classes.jar" | grep -Fxq "$sdk_verification_path"; then
    echo "Expected $sdk_verification_path in $aar_path classes.jar" >&2
    exit 1
  fi
}

bazel_args=(
  --announce_rc
  --config=ci
  --config=release-android
  --define="pom_version=$version"
)

if [ "$include_sdk_verification_file" = "true" ]; then
  bazel_args+=(--define=android_sdk_verification_file=true)
fi

./bazelw build \
  "${bazel_args[@]}" \
  //:capture_aar_with_artifacts //:capture_symbols

if [ "$include_sdk_verification_file" = "true" ]; then
  verify_sdk_verification_file "bazel-bin/capture.aar"
fi

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
