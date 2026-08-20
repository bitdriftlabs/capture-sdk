#!/bin/bash

set -euo pipefail

# These names partition CI work by runner capability. "linux_runner" covers
# every test that can run on Linux, including Android host tests;
# "macos_runner" contains the iOS XCTest suite. "all" runs the complete test
# set in one Bazel invocation on a local macOS machine.
if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <all|linux_runner|macos_runner> <full|affected> <affected-targets-path>"
  exit 2
fi

slice="$1"
mode="$2"
affected_targets_path="$3"

case "$mode" in
  full|affected)
    ;;
  *)
    echo "Unsupported test mode: $mode"
    exit 2
    ;;
esac

run_bazel_tests() {
  local use_ios_test_environment="$1"
  local include_libunwind="$2"
  local use_linux_hermetic_llvm="$3"
  local test_tag_filters="$4"
  local build_tag_filters="$5"
  shift 5

  local bazel_args=(test --config ci --remote_download_minimal --test_output=errors --test_env=RUST_LOG=debug)
  if [[ -n "$test_tag_filters" ]]; then
    bazel_args+=(--test_tag_filters="$test_tag_filters")
  fi
  if [[ -n "$build_tag_filters" ]]; then
    bazel_args+=(--build_tag_filters="$build_tag_filters")
  fi
  if [[ "$include_libunwind" == "true" ]]; then
    bazel_args+=(--config libunwind)
  fi
  if [[ "$use_linux_hermetic_llvm" == "true" ]]; then
    bazel_args+=(--config linux-hermetic-llvm)
  fi
  if [[ "$use_ios_test_environment" == "true" ]]; then
    bazel_args+=(--test_env=REUSE_GLOBAL_SIMULATOR=1 --test_env=STARTUP_TIMEOUT_SEC=300)
    env -u ANDROID_NDK_HOME ./bazelw "${bazel_args[@]}" "$@"
  else
    ./bazelw "${bazel_args[@]}" "$@"
  fi
}

run_linux_runner() {
  if [[ "$mode" == "full" ]]; then
    run_bazel_tests false true true -macos_only -macos_only //platform/... //test/...
  elif [[ -s "$affected_targets_path" ]]; then
    run_bazel_tests false true true -macos_only -macos_only --target_pattern_file="$affected_targets_path"
  else
    echo "No affected Linux-runner test targets; skipping tests."
  fi
}

run_macos_runner() {
  # Keep the established full XCTest suite even when the Bazel Diff selection
  # is affected-only. The affected targets still drive the Clippy invocation.
  echo "Running the full iOS test suite for $mode mode."
  local ios_test_targets=()
  while IFS= read -r target; do
    ios_test_targets+=("$target")
  done < <(./bazelw query 'kind(ios_unit_test, //test/platform/swift/unit_integration/...)')
  run_bazel_tests true false false macos_only '' "${ios_test_targets[@]}"
}

case "$slice" in
  linux_runner)
    run_linux_runner
    ;;
  macos_runner)
    run_macos_runner
    ;;
  all)
    if [[ "$mode" != "full" ]]; then
      echo "The all slice only supports full mode."
      exit 2
    fi
    if [[ "$(uname -s)" != "Darwin" ]]; then
      echo "The all slice is only supported on macOS."
      exit 2
    fi
    run_bazel_tests true false false '' '' //platform/... //test/...
    ;;
  *)
    echo "Unsupported test slice: $slice"
    exit 2
    ;;
esac
