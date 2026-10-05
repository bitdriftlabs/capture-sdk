#!/usr/bin/env bash

readonly emulator_serial="${ANDROID_SERIAL:-emulator-5554}"
readonly package_name="io.bitdrift.gradletestapp"
readonly main_activity="$package_name/.ui.activities.MainActivity"
readonly shared_prefs_path="shared_prefs/${package_name}_preferences.xml"
readonly logs_dir="${LOGS_DIR:-fatal-issue-reports-logs}"
readonly apk_path="${APK_PATH:-platform/jvm/gradle-test-app/build/outputs/apk/debug/gradle-test-app-debug.apk}"
readonly log_tag="BitdriftE2E"
readonly sdk_log_tag="bitdrift"
readonly maestro_flow="tools/maestro/force-app-exit.yaml"
readonly maestro_version="${MAESTRO_VERSION:-2.10.0}"
readonly sdk_start_timeout_seconds=90
readonly trigger_timeout_seconds=60
readonly exit_timeout_seconds=90
readonly report_timeout_seconds=120
readonly foreground_timeout_seconds=30
readonly emulator_ready_attempts=120

# shellcheck source=ci/android_emulator.sh
source "$(dirname "${BASH_SOURCE[0]}")/android_emulator.sh"

adb_shell() {
  adb -s "$emulator_serial" shell "$@"
}

seed_gradle_test_app_settings() {
  local api_url="$1"
  local api_key="$2"
  local escaped_api_key
  local prefs_xml

  escaped_api_key="$(printf '%s' "$api_key" | sed -e 's/&/\&amp;/g' -e 's/"/\&quot;/g' -e "s/'/\\&apos;/g" -e 's/</\&lt;/g' -e 's/>/\&gt;/g')"
  prefs_xml="$(cat <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name='apiUrl'>$api_url</string>
    <string name='api_key'>$escaped_api_key</string>
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

app_pid() {
  adb_shell pidof "$package_name" | tr -d '\r' || true
}

wait_for_process_exit() {
  for second in $(seq 1 "$exit_timeout_seconds"); do
    if [[ -z "$(app_pid)" ]]; then
      return 0
    fi
    if ((second % 3 == 1)); then
      adb_shell input keyevent KEYCODE_DPAD_DOWN >/dev/null 2>&1 &
    fi
    sleep 1
  done
  return 1
}

save_logcat() {
  local name="$1"
  adb -s "$emulator_serial" logcat -d -v threadtime > "$logs_dir/$name.log" || true
}

save_maestro_debug_output() {
  local name="$1"
  local latest_output
  latest_output="$(find "$HOME/.maestro/tests" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -1)"
  if [[ -n "$latest_output" ]]; then
    mkdir -p "$logs_dir/maestro"
    cp -R "$latest_output" "$logs_dir/maestro/$name" || true
  fi
}

focused_window() {
  adb_shell dumpsys window | grep -E 'mCurrentFocus|mFocusedWindow' | tr -d '\r' || true
}

bring_app_to_foreground() {
  for _ in $(seq 1 "$foreground_timeout_seconds"); do
    adb_shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb_shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_shell cmd statusbar collapse >/dev/null 2>&1 || true
    if focused_window | grep -qF "$package_name"; then
      return 0
    fi
    adb_shell am start -n "$main_activity" >/dev/null 2>&1 || true
    sleep 1
  done
  return 1
}

stop_maestro() {
  local maestro_pid="$1"
  if kill -0 "$maestro_pid" 2>/dev/null; then
    kill "$maestro_pid" 2>/dev/null || true
  fi
  wait "$maestro_pid" 2>/dev/null || true
}

# Usage: run_case <name> <app exit reason> <expected IssueReportCallback reportType> [save success logs]
run_case() {
  local name="$1"
  local reason="$2"
  local expected_type="$3"
  local save_success_logs="${4:-true}"
  local maestro_pid

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

  if ! bring_app_to_foreground; then
    echo "::error::$name: app did not reach the foreground (focus: $(focused_window))"
    save_logcat "$name-foreground"
    echo "::endgroup::"
    return 1
  fi

  maestro test -e APP_EXIT_REASON="$reason" "$maestro_flow" &
  maestro_pid=$!
  if ! wait_for_logcat "Triggering app exit reason=$reason" "$trigger_timeout_seconds"; then
    stop_maestro "$maestro_pid"
    echo "::error::$name: app never triggered $reason (focus: $(focused_window))"
    save_logcat "$name-trigger"
    save_maestro_debug_output "$name"
    echo "::endgroup::"
    return 1
  fi

  if ! wait_for_process_exit; then
    stop_maestro "$maestro_pid"
    echo "::error::$name: app process did not exit after $reason"
    save_logcat "$name-exit"
    echo "::endgroup::"
    return 1
  fi
  stop_maestro "$maestro_pid"
  if [[ "$save_success_logs" == "true" ]]; then
    save_logcat "$name-exit"
  fi

  adb -s "$emulator_serial" logcat -c
  adb_shell am start -n "$main_activity"
  if ! wait_for_logcat "onBeforeReportSend reportType=$expected_type " "$report_timeout_seconds"; then
    echo "::error::$name: no '$expected_type' report was processed on the next launch"
    save_logcat "$name-next-launch"
    echo "::endgroup::"
    return 1
  fi
  if [[ "$save_success_logs" == "true" ]]; then
    save_logcat "$name-next-launch"
  fi

  echo "$name: '$expected_type' report processed on the next launch"
  echo "::endgroup::"
}

install_maestro() {
  if ! command -v maestro >/dev/null; then
    for attempt in 1 2 3; do
      if curl -fsSL "https://get.maestro.mobile.dev" | MAESTRO_VERSION="$maestro_version" bash &&
        [[ -x "$HOME/.maestro/bin/maestro" ]]; then
        break
      fi
      echo "Maestro install attempt $attempt failed"
      sleep $((attempt * 10))
    done
    export PATH="$PATH:$HOME/.maestro/bin"
  fi
  if ! command -v maestro >/dev/null; then
    echo "::error::Maestro is not installed"
    return 1
  fi
  export MAESTRO_CLI_NO_ANALYTICS=1
  export ANDROID_SERIAL="$emulator_serial"
}

# Usage: prepare_emulator_and_app <api url> <api key>
prepare_emulator_and_app() {
  local api_url="$1"
  local api_key="$2"
  local install_attempt=1

  if [[ ! -f "$apk_path" ]]; then
    echo "Expected APK not found at $apk_path"
    return 1
  fi

  adb start-server
  wait_for_android_emulator_ready "$emulator_serial" "$emulator_ready_attempts"
  adb_shell settings put global hide_error_dialogs 1
  adb_shell svc power stayon true || true
  adb_shell locksettings set-disabled true || true

  until adb -s "$emulator_serial" install -r "$apk_path"; do
    save_logcat "install-attempt-$install_attempt"
    if [[ "$install_attempt" -ge 3 ]]; then
      echo "::error::Failed to install $apk_path"
      return 1
    fi
    install_attempt=$((install_attempt + 1))
    wait_for_android_emulator_ready "$emulator_serial" "$emulator_ready_attempts"
  done
  adb_shell input keyevent 82 >/dev/null 2>&1 || true
  seed_gradle_test_app_settings "$api_url" "$api_key"
}
