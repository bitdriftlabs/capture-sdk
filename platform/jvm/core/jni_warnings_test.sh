#!/bin/bash

set -euo pipefail

test_binary="$TEST_SRCDIR/$TEST_WORKSPACE/$1"
shift

output_file="$(mktemp)"
trap 'rm -f "$output_file"' EXIT

set +e
"$test_binary" "$@" >"$output_file" 2>&1
status=$?
set -e

cat "$output_file"

if grep -Eq 'WARNING: JNI|WARNING in native method|JNI WARNING' "$output_file"; then
  echo "-Xcheck:jni reported a JNI warning" >&2
  exit 1
fi

exit "$status"
