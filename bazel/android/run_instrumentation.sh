#!/usr/bin/env bash
# Installs a self-instrumenting test APK on the connected device and runs its instrumentation.
#
# Usage: run_instrumentation.sh <apk> <package> <runner> [am instrument -e key value ...]
#
# The device is the one adb selects; set ANDROID_SERIAL to choose among several. Extra arguments
# are passed to `am instrument`, so a single class can be selected with
#   --test_arg=-e --test_arg=class --test_arg=io.example.FooTest
# Any arguments in INSTRUMENTATION_ARGS (space separated) are appended as well.

set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: $0 <apk> <package> <runner> [am instrument args...]" >&2
  exit 2
fi

apk=$1
package=$2
runner=$3
shift 3

# Bazel runs tests from the runfiles tree; `bazel run` sets BUILD_WORKSPACE_DIRECTORY instead.
if [[ ! -f "${apk}" && -n "${RUNFILES_DIR:-}" ]]; then
  apk="${RUNFILES_DIR}/_main/${apk}"
fi

find_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  local sdk
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${HOME:-}/Library/Android/sdk" "${HOME:-}/Android/Sdk"; do
    if [[ -n "${sdk}" && -x "${sdk}/platform-tools/adb" ]]; then
      echo "${sdk}/platform-tools/adb"
      return
    fi
  done
  echo "adb not found; set ANDROID_HOME or put adb on PATH" >&2
  exit 1
}

adb=$(find_adb)

"${adb}" wait-for-device
echo "Device: $("${adb}" shell getprop ro.product.model | tr -d '\r') (API $("${adb}" shell getprop ro.build.version.sdk | tr -d '\r'))"

# A previous install signed with another key (for example the Gradle build) blocks `install -r`.
"${adb}" uninstall "${package}" >/dev/null 2>&1 || true
"${adb}" install -r -t -g "${apk}"

extra_args=("$@")
if [[ -n "${INSTRUMENTATION_ARGS:-}" ]]; then
  read -r -a env_args <<<"${INSTRUMENTATION_ARGS}"
  extra_args+=("${env_args[@]}")
fi

output_file=$(mktemp)
trap 'rm -f "${output_file}"' EXIT

# `am instrument` exits 0 even when tests fail, so the verdict comes from its output.
"${adb}" shell am instrument -w -r "${extra_args[@]+"${extra_args[@]}"}" "${package}/${runner}" \
  | tr -d '\r' | tee "${output_file}" \
  | grep -E '^(INSTRUMENTATION_STATUS: (class|test|stack)=|INSTRUMENTATION_STATUS_CODE: -[0-9]|INSTRUMENTATION_(RESULT|CODE|FAILED)|OK \(|FAILURES!!!|Tests run:)' || true

# Pull the raw instrumentation output into the Bazel test outputs when available.
if [[ -n "${TEST_UNDECLARED_OUTPUTS_DIR:-}" ]]; then
  cp "${output_file}" "${TEST_UNDECLARED_OUTPUTS_DIR}/instrumentation.txt"
fi

# Per-test status codes: -1 error, -2 failure, -3 ignored, -4 assumption failure (skipped).
skipped=$(grep -c '^INSTRUMENTATION_STATUS_CODE: -[34]$' "${output_file}" || true)
echo "Skipped (ignored or assumption failed): ${skipped}"

if grep -q '^INSTRUMENTATION_FAILED' "${output_file}" \
  || grep -q '^INSTRUMENTATION_STATUS_CODE: -[12]$' "${output_file}" \
  || grep -q '^FAILURES!!!' "${output_file}" \
  || ! grep -q '^OK (' "${output_file}"; then
  echo "Instrumentation failed" >&2
  exit 1
fi

echo "Instrumentation passed"
