#!/usr/bin/env bash

set -euo pipefail

readonly iterations="${1:-100}"
readonly crash_loop_api_key="${CRASH_LOOP_API_KEY:-}"
readonly crash_loop_api_url="${CRASH_LOOP_API_URL:-https://api.bitdrift.dev}"
readonly attempts_per_iteration=3
readonly report_upload_grace_seconds=5

# name|app exit reason|expected IssueReportCallback reportType
readonly cases=(
  "anr|ANR_DEADLOCK|ANR"
  "jvm_crash|APP_CRASH_REGULAR_JVM_EXCEPTION|Crash"
  "native_crash|NATIVE_SIGSEGV|Native Crash"
)

# shellcheck source=ci/gradle_test_app_fatal_issues.sh
source "$(dirname "${BASH_SOURCE[0]}")/gradle_test_app_fatal_issues.sh"

if ! [[ "$iterations" =~ ^[0-9]+$ ]] || [[ "$iterations" -lt 1 ]]; then
  echo "iterations must be a positive integer"
  exit 1
fi

if [[ -z "$crash_loop_api_key" ]]; then
  echo "CRASH_LOOP_API_KEY must be set"
  exit 1
fi

recover_emulator() {
  adb kill-server || true
  adb start-server
  wait_for_android_emulator_ready "$emulator_serial" "$emulator_ready_attempts"
}

mkdir -p "$logs_dir"
install_maestro
prepare_emulator_and_app "$crash_loop_api_url" "$crash_loop_api_key"

triggered=(0 0 0)
reported=(0 0 0)
retried_iterations=0
failed_iterations=()

for iteration in $(seq 1 "$iterations"); do
  case_index=$((RANDOM % ${#cases[@]}))
  IFS='|' read -r name reason expected_type <<< "${cases[$case_index]}"
  triggered[case_index]=$((triggered[case_index] + 1))
  echo "Iteration $iteration/$iterations: $name"

  for attempt in $(seq 1 "$attempts_per_iteration"); do
    if run_case "$iteration-$name-attempt-$attempt" "$reason" "$expected_type" false; then
      reported[case_index]=$((reported[case_index] + 1))
      sleep "$report_upload_grace_seconds"
      break
    fi

    if [[ "$attempt" -eq "$attempts_per_iteration" ]]; then
      failed_iterations+=("$iteration:$name")
      break
    fi

    retried_iterations=$((retried_iterations + 1))
    echo "Retrying iteration $iteration ($name), attempt $((attempt + 1))/$attempts_per_iteration"
    recover_emulator
  done
done

summary="| Case | Triggered | Reported |"$'\n'"|---|---|---|"
for case_index in "${!cases[@]}"; do
  IFS='|' read -r name _ _ <<< "${cases[$case_index]}"
  summary+=$'\n'"| $name | ${triggered[case_index]} | ${reported[case_index]} |"
done
summary+=$'\n\n'"Iterations: $iterations · Retried: $retried_iterations · Failed: ${#failed_iterations[@]}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '%s\n' "$summary" >> "$GITHUB_STEP_SUMMARY"
fi
printf '%s\n' "$summary"

if [[ "${#failed_iterations[@]}" -gt 0 ]]; then
  echo "Failed iterations: ${failed_iterations[*]}"
  exit 1
fi
