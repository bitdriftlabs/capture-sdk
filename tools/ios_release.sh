#!/bin/bash

set -euo pipefail

usage() {
  echo "Usage: $0 [<library> <version> [default|swift-6.4]]" >&2
}

if [ $# -eq 0 ]; then
  # Use time interval if no version was provided
  version="$(date +%s)"
  variant="default"
elif [ $# -eq 2 ]; then
  version="$2"
  variant="default"
elif [ $# -eq 3 ]; then
  version="$2"
  variant="$3"
else
  usage
  exit 1
fi

readonly version="$version"
readonly variant="$variant"

case "$variant" in
  default)
    xcode_version="26.0.1"
    output_zip="Capture.ios.zip"
    build_doccarchive="true"
    ;;
  swift-6.4)
    xcode_version="27.0"
    output_zip="Capture-swift-6.4.ios.zip"
    build_doccarchive="false"
    ;;
  *)
    echo "Unknown variant \"$variant\"" >&2
    usage
    exit 1
    ;;
esac

readonly xcode_version="$xcode_version"
readonly output_zip="$output_zip"
readonly build_doccarchive="$build_doccarchive"

# Emitted as `key=value` lines so that CI can consume the plan through $GITHUB_OUTPUT.
if [[ -n "${IOS_RELEASE_DRY_RUN:-}" ]]; then
  echo "version=$version"
  echo "variant=$variant"
  echo "xcode_version=$xcode_version"
  echo "output_zip=$output_zip"
  echo "build_doccarchive=$build_doccarchive"
  exit 0
fi

echo "+++ Version"
echo "$version"

echo "+++ Building Capture.xcframework ($variant)"

targets=("//:ios_dist")
if [[ "$build_doccarchive" == "true" ]]; then
  targets+=("//:ios_doccarchive")
fi

# --xcode_version goes last so that it wins over the one `--config=ci` pins.
./bazelw build \
  --announce_rc \
  --config=ci \
  --config=release-ios \
  --define ios_produce_framework_plist=true \
  --xcode_version="$xcode_version" \
  "${targets[@]}"

mkdir -p dist
mv -f bazel-bin/Capture.ios.zip "dist/$output_zip"

if [[ "$build_doccarchive" == "true" ]]; then
  mv -f bazel-bin/Capture.doccarchive.ios.zip dist/Capture.doccarchive.ios.zip
fi
