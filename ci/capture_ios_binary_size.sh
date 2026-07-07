#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Prints the size in KB of the arm64 Capture SDK binary from the prebuilt iOS
XCFramework used by the hello world sample app.

Environment variables:
  IOS_CAPTURE_BINARY_PATH
      Optional override for the Capture.framework/Capture binary path.
  IOS_CAPTURE_BINARY_FIND_ROOTS
      Space-separated directories searched for the built Capture binary when no
      explicit IOS_CAPTURE_BINARY_PATH is provided. Default: "bazel-bin bazel-out"

Output:
  IOS_CAPTURE_BINARY_SIZE_KB=<binary size in KB>
  IOS_CAPTURE_BINARY_PATH=<path to the measured binary>
EOF
}

if [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

FIND_ROOTS="${IOS_CAPTURE_BINARY_FIND_ROOTS:-bazel-bin bazel-out}"

find_binary_path() {
  local root
  local candidate
  for root in $FIND_ROOTS; do
    if [[ ! -d "$root" ]]; then
      continue
    fi

    candidate="$(find -L "$root" -path '*Capture.xcframework/ios-arm64/Capture.framework/Capture' -type f -print -quit)"
    if [[ -n "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
}

resolve_size_bytes() {
  local path="$1"
  python3 - "$path" <<'PY'
import os
import sys

print(os.path.getsize(sys.argv[1]))
PY
}

BINARY_PATH="${IOS_CAPTURE_BINARY_PATH:-$(find_binary_path)}"

if [[ ! -f "$BINARY_PATH" ]]; then
  echo "Expected Capture SDK binary not found at $BINARY_PATH" >&2
  exit 1
fi

BINARY_SIZE_BYTES="$(resolve_size_bytes "$BINARY_PATH")"

IOS_CAPTURE_BINARY_SIZE_KB="$((BINARY_SIZE_BYTES / 1024))"
echo "IOS_CAPTURE_BINARY_SIZE_KB=$IOS_CAPTURE_BINARY_SIZE_KB"
echo "IOS_CAPTURE_BINARY_PATH=$BINARY_PATH"
