#!/usr/bin/env bash

set -euo pipefail

archive="${1:?Expected the benchmark runner archive}"
outputs="${2:?Expected the benchmark output directory}"
mkdir -p "$outputs"
outputs="$(cd "$outputs" && pwd)"
output="$(mktemp -d)"
simulator=""
cleanup() {
  if [[ -n "$simulator" ]]; then
    xcrun simctl delete "$simulator" || true
  fi
  rm -rf "$output"
}
trap cleanup EXIT

# shellcheck source=ci/ios_ci_simulator.sh
source "$(dirname "${BASH_SOURCE[0]}")/ios_ci_simulator.sh"
tar -xzf "$archive" -C "$output"
simulator="$(create_ios_ci_simulator)"
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b

export TEST_SRCDIR="$output/run_benchmarks_xctest.runfiles"
export TEST_WORKSPACE="_main"
export TEST_UNDECLARED_OUTPUTS_DIR="$outputs"
export TEST_PREMATURE_EXIT_FILE="$output/premature-exit"
export XML_OUTPUT_FILE="$outputs/test.xml"
export APPLE_SDK_PLATFORM="iPhoneSimulator"
export STARTUP_TIMEOUT_SEC=300
cd "$TEST_SRCDIR/$TEST_WORKSPACE"
"$output/run_benchmarks_xctest" --destination="platform=iOS Simulator,id=$simulator" 2>&1 |
  tee "$outputs/xctest.log"
