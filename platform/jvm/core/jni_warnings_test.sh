#!/bin/bash

set -euo pipefail

test_binary="$TEST_SRCDIR/$TEST_WORKSPACE/__TEST_BINARY_RUNFILES_PATH__"
jdk_home="$TEST_SRCDIR/$TEST_WORKSPACE/__JAVA_HOME_RUNFILES_PATH__"

# Bazel resolves this path from JavaRuntimeInfo, without exposing a generated bzlmod repository
# name. jni 0.21's invocation API finds libjvm through JAVA_HOME.
export JAVA_HOME="$jdk_home"

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
