#!/bin/bash

set -euo pipefail

# These names partition CI work by runner capability. "linux_runner" covers
# every lint target that can run on Linux, including Android host targets;
# "macos_runner" covers the macOS/iOS-only targets. "all" runs both slices on
# a local macOS machine in one Bazel invocation.
if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <all|linux_runner|macos_runner> <affected|full> <affected-targets-path>"
  exit 2
fi

slice="$1"
mode="$2"
affected_targets_path="$3"

case "$mode" in
  full|affected)
    ;;
  *)
    echo "Unsupported Clippy mode: $mode"
    exit 2
    ;;
esac

run_clippy() {
  local build_tag_filters="$1"
  shift

  local bazel_configs=(--config clippy)
  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    bazel_configs=(--config ci "${bazel_configs[@]}")
  fi

  ./bazelw build "${bazel_configs[@]}" --build_tag_filters="$build_tag_filters" "$@"
}

run_linux_runner() {
  local targets=()
  if [[ "$mode" == "full" ]]; then
    targets=(//... //platform/shared:build_script_)
  elif [[ -s "$affected_targets_path" ]]; then
    targets=(--target_pattern_file="$affected_targets_path")
  else
    echo "No affected Linux-runner Clippy targets; skipping."
    return 0
  fi

  run_clippy clippy,-macos_only "${targets[@]}"
}

run_macos_runner() {
  local targets=()
  if [[ "$mode" == "full" ]]; then
    targets=(//...)
  elif [[ -s "$affected_targets_path" ]]; then
    targets=(--target_pattern_file="$affected_targets_path")
  else
    echo "No affected macOS-runner Clippy targets; skipping."
    return 0
  fi

  run_clippy clippy_macos "${targets[@]}"
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
    run_clippy clippy //... //platform/shared:build_script_
    ;;
  *)
    echo "Unsupported Clippy slice: $slice"
    exit 2
    ;;
esac
