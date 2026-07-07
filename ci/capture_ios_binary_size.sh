#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Prints the size in KB of the arm64 Capture SDK binary produced for the iOS
XCFramework used by the hello world sample app.

Environment variables:
  IOS_CAPTURE_BINARY_PATH
      Optional override for the Capture.framework/Capture binary path.
  IOS_CAPTURE_BINARY_TARGET
      Bazel target that materializes the Capture XCFramework. Default:
      //examples/swift/hello_world:expanded_xcframework
  IOS_CAPTURE_BINARY_SKIP_BUILD
      Set to 1 to skip the Bazel build step and only measure the existing file.

Output:
  IOS_CAPTURE_BINARY_SIZE_KB=<binary size in KB>
  IOS_CAPTURE_BINARY_PATH=<path to the measured binary>
EOF
}

if [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

BINARY_TARGET="${IOS_CAPTURE_BINARY_TARGET:-//examples/swift/hello_world:expanded_xcframework}"
BINARY_PATH="${IOS_CAPTURE_BINARY_PATH:-bazel-bin/examples/swift/hello_world/Capture.xcframework/ios-arm64/Capture.framework/Capture}"

if [[ "${IOS_CAPTURE_BINARY_SKIP_BUILD:-0}" != "1" ]]; then
  ./bazelw build \
    --config ci \
    --config release-ios \
    --cpu=ios_arm64 \
    "$BINARY_TARGET"
fi

if [[ ! -f "$BINARY_PATH" ]]; then
  echo "Expected Capture SDK binary not found at $BINARY_PATH" >&2
  exit 1
fi

if stat -f%z "$BINARY_PATH" > /dev/null 2>&1; then
  BINARY_SIZE_BYTES="$(stat -f%z "$BINARY_PATH")"
else
  BINARY_SIZE_BYTES="$(stat -c%s "$BINARY_PATH")"
fi

IOS_CAPTURE_BINARY_SIZE_KB="$((BINARY_SIZE_BYTES / 1024))"
echo "IOS_CAPTURE_BINARY_SIZE_KB=$IOS_CAPTURE_BINARY_SIZE_KB"
echo "IOS_CAPTURE_BINARY_PATH=$BINARY_PATH"
