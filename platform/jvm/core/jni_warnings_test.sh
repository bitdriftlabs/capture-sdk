#!/bin/bash

set -euo pipefail

test_binary="$TEST_SRCDIR/$TEST_WORKSPACE/$1"
shift

# jni 0.21's invocation API locates libjvm from JAVA_HOME. Locate Bazel's selected JDK without
# coupling the test to a generated bzlmod repository name.
for jdk_home in "$TEST_SRCDIR"/*; do
  if [[ -f "$jdk_home/lib/server/libjvm.dylib" || -f "$jdk_home/lib/server/libjvm.so" || -f "$jdk_home/bin/server/jvm.dll" ]]; then
    export JAVA_HOME="$jdk_home"
    break
  fi
done

: "${JAVA_HOME:?unable to find Bazel JDK in test runfiles}"

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
