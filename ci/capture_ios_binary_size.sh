#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Prints the size in KB of the arm64 Capture SDK binary from a prebuilt iOS
XCFramework artifact.

Environment variables:
  IOS_CAPTURE_BINARY_PATH
      Optional override for either:
      - the Capture.framework/Capture binary path
      - a Capture.zip XCFramework archive path
  IOS_CAPTURE_BINARY_FIND_ROOTS
      Space-separated directories searched when no explicit
      IOS_CAPTURE_BINARY_PATH is provided. Default: "bazel-bin bazel-out"

Output:
  IOS_CAPTURE_BINARY_SIZE_KB=<binary size in KB>
  IOS_CAPTURE_BINARY_PATH=<path to the measured binary or archive>
EOF
}

if [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

FIND_ROOTS="${IOS_CAPTURE_BINARY_FIND_ROOTS:-bazel-bin bazel-out}"
ARCHIVE_MEMBER_PATH="Capture.xcframework/ios-arm64/Capture.framework/Capture"

find_capture_artifact() {
  local root
  local candidate
  for root in $FIND_ROOTS; do
    if [[ ! -d "$root" ]]; then
      echo "Skipping missing search root: $root" >&2
      continue
    fi

    echo "Searching for Capture SDK binary under: $root" >&2
    candidate="$(find -L "$root" -path '*Capture.xcframework/ios-arm64/Capture.framework/Capture' -type f -print -quit)"
    if [[ -n "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi

    echo "Searching for Capture SDK XCFramework archive under: $root" >&2
    candidate="$(find -L "$root" -name 'Capture.zip' -type f -print -quit)"
    if [[ -n "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
}

resolve_size_bytes() {
  local path="$1"
  python3 - "$path" "$ARCHIVE_MEMBER_PATH" <<'PY'
import os
import sys
import zipfile

path = sys.argv[1]
archive_member = sys.argv[2]

if path.endswith(".zip"):
    with zipfile.ZipFile(path) as archive:
        try:
            print(archive.getinfo(archive_member).file_size)
        except KeyError:
            print(
                f"Expected Capture SDK binary not found in archive member {archive_member}",
                file=sys.stderr,
            )
            sys.exit(1)
else:
    print(os.path.getsize(path))
PY
}

BINARY_PATH="${IOS_CAPTURE_BINARY_PATH:-$(find_capture_artifact)}"

if [[ ! -f "$BINARY_PATH" ]]; then
  echo "Expected Capture SDK binary not found at $BINARY_PATH" >&2
  echo "Searched roots: $FIND_ROOTS" >&2
  exit 1
fi

BINARY_SIZE_BYTES="$(resolve_size_bytes "$BINARY_PATH")"

IOS_CAPTURE_BINARY_SIZE_KB="$((BINARY_SIZE_BYTES / 1024))"
echo "IOS_CAPTURE_BINARY_SIZE_KB=$IOS_CAPTURE_BINARY_SIZE_KB"
echo "IOS_CAPTURE_BINARY_PATH=$BINARY_PATH"
