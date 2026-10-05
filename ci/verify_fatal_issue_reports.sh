#!/usr/bin/env bash

set -euo pipefail

# name|app exit reason|expected IssueReportCallback reportType
readonly cases=(
  "anr|ANR_GENERIC|ANR"
  "jvm_crash|APP_CRASH_REGULAR_JVM_EXCEPTION|Crash"
  "native_crash|NATIVE_SIGSEGV|Native Crash"
)

# shellcheck source=ci/gradle_test_app_fatal_issues.sh
source "$(dirname "${BASH_SOURCE[0]}")/gradle_test_app_fatal_issues.sh"

readonly results_file="$logs_dir/results.txt"

mkdir -p "$logs_dir"
install_maestro
prepare_emulator_and_app "https://127.0.0.1" "fatal-issue-reports-e2e"
api_level="$(adb_shell getprop ro.build.version.sdk | tr -d '\r')"

failed_cases=()
: > "$results_file"
summary="| Case | API $api_level |"$'\n'"|---|---|"
for test_case in "${cases[@]}"; do
  IFS='|' read -r name reason expected_type <<< "$test_case"
  if run_case "$name" "$reason" "$expected_type"; then
    summary+=$'\n'"| $name | pass |"
    echo "$name|pass" >> "$results_file"
  else
    failed_cases+=("$name")
    summary+=$'\n'"| $name | fail |"
    echo "$name|fail" >> "$results_file"
  fi
done

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '%s\n' "$summary" >> "$GITHUB_STEP_SUMMARY"
fi
printf '%s\n' "$summary"

if [[ "${#failed_cases[@]}" -gt 0 ]]; then
  echo "Failed cases on API $api_level: ${failed_cases[*]}"
  exit 1
fi
