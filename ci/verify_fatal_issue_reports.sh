#!/usr/bin/env bash

set -euo pipefail

readonly emulator_serial="${ANDROID_SERIAL:-emulator-5554}"
readonly package_name="io.bitdrift.gradletestapp"
readonly main_activity="$package_name/.ui.activities.MainActivity"
readonly shared_prefs_path="shared_prefs/${package_name}_preferences.xml"
readonly apk_path="${APK_PATH:-platform/jvm/gradle-test-app/build/outputs/apk/debug/gradle-test-app-debug.apk}"
readonly logs_dir="${LOGS_DIR:-fatal-issue-reports-logs}"
readonly results_file="$logs_dir/results.txt"
readonly log_tag="BitdriftE2E"
readonly sdk_log_tag="bitdrift"
readonly maestro_flow="tools/maestro/force-app-exit.yaml"
readonly sdk_start_timeout_seconds=90
readonly trigger_timeout_seconds=30
readonly exit_timeout_seconds=90
readonly report_timeout_seconds=120
readonly emulator_ready_attempts=120

# name|app exit reason|expected IssueReportCallback reportType
readonly cases=(
  "anr|ANR_GENERIC|ANR"
  "jvm_crash|APP_CRASH_REGULAR_JVM_EXCEPTION|Crash"
  "native_crash|NATIVE_SIGSEGV|Native Crash"
)

# shellcheck source=ci/android_emulator.sh
source "$(dirname "${BASH_SOURCE[0]}")/android_emulator.sh"

adb_shell() {
  adb -s "$emulator_serial" shell "$@"
}

seed_gradle_test_app_settings() {
  local prefs_xml
  prefs_xml="$(cat <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name='apiUrl'>https://127.0.0.1</string>
    <string name='api_key'>fatal-issue-reports-e2e</string>
</map>
EOF
)"

  printf '%s' "$prefs_xml" | adb_shell "run-as $package_name sh -c 'mkdir -p shared_prefs && cat > $shared_prefs_path'"
}

wait_for_logcat() {
  local pattern="$1"
  local timeout_seconds="$2"
  local tag="${3:-$log_tag}"

  for _ in $(seq 1 "$timeout_seconds"); do
    if adb -s "$emulator_serial" logcat -d -s "$tag" | grep -qF -- "$pattern"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_process_exit() {
  for _ in $(seq 1 "$exit_timeout_seconds"); do
    if [[ -z "$(adb_shell pidof "$package_name" | tr -d '\r')" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

save_logcat() {
  local name="$1"
  adb -s "$emulator_serial" logcat -d -v threadtime > "$logs_dir/$name.log" || true
}

run_case() {
  local name="$1"
  local reason="$2"
  local expected_type="$3"

  echo "::group::$name ($reason)"

  adb_shell am force-stop "$package_name"
  adb -s "$emulator_serial" logcat -c

  adb_shell am start -n "$main_activity"
  if ! wait_for_logcat "SDK started successfully" "$sdk_start_timeout_seconds" "$sdk_log_tag"; then
    echo "::error::$name: SDK did not start"
    save_logcat "$name-sdk-start"
    echo "::endgroup::"
    return 1
  fi

  maestro test -e APP_EXIT_REASON="$reason" "$maestro_flow" ||
    echo "Maestro exited with an error, which is expected when the app exits mid-flow"
  if ! wait_for_logcat "Triggering app exit reason=$reason" "$trigger_timeout_seconds"; then
    echo "::error::$name: app never triggered $reason"
    save_logcat "$name-trigger"
    echo "::endgroup::"
    return 1
  fi

  if ! wait_for_process_exit; then
    echo "::error::$name: app process did not exit after $reason"
    save_logcat "$name-exit"
    echo "::endgroup::"
    return 1
  fi
  save_logcat "$name-exit"

  adb -s "$emulator_serial" logcat -c
  adb_shell am start -n "$main_activity"
  if ! wait_for_logcat "onBeforeReportSend reportType=$expected_type " "$report_timeout_seconds"; then
    echo "::error::$name: no '$expected_type' report was processed on the next launch"
    save_logcat "$name-next-launch"
    echo "::endgroup::"
    return 1
  fi
  save_logcat "$name-next-launch"

  echo "$name: '$expected_type' report processed on the next launch"
  echo "::endgroup::"
}

if [[ ! -f "$apk_path" ]]; then
  echo "Expected APK not found at $apk_path"
  exit 1
fi

mkdir -p "$logs_dir"

if ! command -v maestro >/dev/null; then
  curl -Ls "https://get.maestro.mobile.dev" | bash
  export PATH="$PATH:$HOME/.maestro/bin"
fi
export MAESTRO_CLI_NO_ANALYTICS=1
export ANDROID_SERIAL="$emulator_serial"

adb start-server
wait_for_android_emulator_ready "$emulator_serial" "$emulator_ready_attempts"
adb_shell settings put global hide_error_dialogs 1
api_level="$(adb_shell getprop ro.build.version.sdk | tr -d '\r')"

adb -s "$emulator_serial" install -r "$apk_path"
adb_shell input keyevent 82 >/dev/null 2>&1 || true
seed_gradle_test_app_settings

failed_cases=()
: > "$results_file"
summary="| Case | API $api_level |"$'\n'"|---|---|"
for test_case in "${cases[@]}"; do
  IFS='|' read -r name reason expected_type <<< "$test_case"
  if run_case "$name" "$reason" "$expected_type"; then
    summary+=$'\n'"| $name | ✅ |"
    echo "$name|pass" >> "$results_file"
  else
    failed_cases+=("$name")
    summary+=$'\n'"| $name | ❌ |"
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
