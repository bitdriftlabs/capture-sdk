#!/bin/bash
set -euo pipefail

# Bazel sometimes uses manifest-based runfiles layout (esp. on macOS)
# We locate the binary path from the runfiles manifest
resolve_from_manifest() {
  local target="$1"
  local manifest="$RUNFILES_MANIFEST_FILE"

  if [[ ! -f "$manifest" ]]; then
    echo >&2 "ERROR: No runfiles manifest found at $manifest"
    exit 1
  fi

  result=$(grep "+$target\$" "$manifest" | cut -d ' ' -f 2- || true)

  if [[ -z "$result" ]]; then
    echo >&2 "ERROR: Could not find $target in manifest"
    exit 1
  fi

  echo "$result"
}

# Try resolving drstring from manifest
if [[ $OSTYPE == darwin* ]]; then
  BIN=$(resolve_from_manifest "DrString/drstring")
else
  BIN=$(resolve_from_manifest "DrString_Linux/usr/bin/drstring")
fi

cd "$BUILD_WORKSPACE_DIRECTORY"
"$BIN" "$@"
