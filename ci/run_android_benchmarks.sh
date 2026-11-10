#!/bin/bash

# Installs the Bazel-built microbenchmark APK on a running emulator, runs its instrumentation
# tests, and saves logcat and the androidx.benchmark results in the output directory.
#
# Usage: ci/run_android_benchmarks.sh <test.apk> <output-dir>

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <test.apk> <output-dir>" >&2
  exit 2
fi

readonly apk="$1"
readonly output_dir="$2"
readonly emulator_serial="${ANDROID_SERIAL:-emulator-5554}"
readonly package="io.bitdrift.microbenchmark.test"
readonly runner="androidx.benchmark.junit4.AndroidBenchmarkRunner"
# The same directory AGP's connected tests use, which the benchmark app may write without
# storage permissions.
readonly device_output_dir="/sdcard/Android/media/${package}/additional_test_output"

# shellcheck source=ci/android_emulator.sh
source "$(dirname "${BASH_SOURCE[0]}")/android_emulator.sh"

wait_for_android_emulator_ready "$emulator_serial"

mkdir -p "$output_dir"
adb -s "$emulator_serial" uninstall "$package" >/dev/null 2>&1 || true
adb -s "$emulator_serial" install -r -t "$apk"
adb -s "$emulator_serial" shell rm -rf "$device_output_dir"
adb -s "$emulator_serial" shell mkdir -p "$device_output_dir"

readonly instrumentation_log="${output_dir}/instrumentation.txt"
adb -s "$emulator_serial" logcat -b all -c
# Mirror the Gradle module's runner arguments: profiling off for real-world timings, and the
# emulator and unlocked-device checks suppressed because CI only has an emulator.
adb -s "$emulator_serial" shell am instrument -w -r \
  -e androidx.benchmark.profiling.mode None \
  -e androidx.benchmark.suppressErrors EMULATOR,UNLOCKED \
  -e additionalTestOutputDir "$device_output_dir" \
  "${package}/${runner}" | tr -d '\r' | tee "$instrumentation_log"

adb -s "$emulator_serial" logcat -b all -d -v threadtime > "${output_dir}/logcat.txt"

# Pull whatever was written even if a test failed, so the check below reports the failure.
adb -s "$emulator_serial" pull "${device_output_dir}/." "$output_dir/" ||
  echo "No benchmark output was pulled from ${device_output_dir}" >&2

# `am instrument` exits zero even when tests fail, so check the reported result.
if grep -q -E '^(FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED)' "$instrumentation_log" ||
  grep -q 'Process crashed' "$instrumentation_log" ||
  ! grep -q -E '^OK \([0-9]+ tests?\)' "$instrumentation_log"; then
  echo "Benchmark instrumentation failed; see ${instrumentation_log}" >&2
  exit 1
fi
