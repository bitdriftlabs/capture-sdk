#!/bin/bash

set -euo pipefail

if [ $# -eq 0 ]; then
  # Use time interval if no version was provided
  version="$(date +%s)"
  readonly version="$version"
elif [ $# -eq 2 ]; then
  readonly version="$2"
else
  echo "Too many arguments provided"
  exit 1
fi

echo "+++ Version"
echo "$version"

echo "+++ Building Capture.xcframework"

./bazelw build \
  --announce_rc \
  --config=ci \
  --config=release-ios \
  --define ios_produce_framework_plist=true \
  //:ios_dist

mkdir -p dist

mv -f bazel-bin/Capture.ios.zip dist/Capture.ios.zip
