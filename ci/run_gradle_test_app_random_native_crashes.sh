#!/usr/bin/env bash

set -euo pipefail

readonly iterations="${1:-5000}"
readonly emulator_serial="${ANDROID_SERIAL:-emulator-5554}"
readonly crash_loop_api_key="${CRASH_LOOP_API_KEY:-}"
readonly shared_prefs_path="shared_prefs/io.bitdrift.gradletestapp_preferences.xml"
readonly apk_path="platform/jvm/gradle-test-app/build/outputs/apk/debug/gradle-test-app-debug.apk"
readonly maestro_flow="tools/maestro/native-crash-loop.yaml"
readonly maestro_retries=3

if ! [[ "$iterations" =~ ^[0-9]+$ ]] || [[ "$iterations" -lt 1 ]]; then
  echo "iterations must be a positive integer"
  exit 1
fi

if [[ -z "$crash_loop_api_key" ]]; then
  echo "CRASH_LOOP_API_KEY must be set"
  exit 1
fi

if [[ ! -f "$apk_path" ]]; then
  echo "Expected APK not found at $apk_path"
  exit 1
fi

wait_for_emulator_ready() {
  adb -s "$emulator_serial" wait-for-device

  for _ in $(seq 1 45); do
    local sys_boot_completed
    local dev_boot_completed
    local boot_anim

    sys_boot_completed="$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    dev_boot_completed="$(adb -s "$emulator_serial" shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r')"
    boot_anim="$(adb -s "$emulator_serial" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"

    if [[ "$sys_boot_completed" == "1" ]] &&
      [[ "$dev_boot_completed" == "1" ]] &&
      [[ "$boot_anim" == "stopped" ]] &&
      adb -s "$emulator_serial" shell cmd package list packages >/dev/null 2>&1; then
      return 0
    fi

    adb reconnect offline >/dev/null 2>&1 || true
    sleep 2
  done

  echo "Timed out waiting for emulator package manager readiness"
  return 1
}

seed_gradle_test_app_settings() {
  local escaped_api_key
  local prefs_xml

  escaped_api_key="$(printf '%s' "$crash_loop_api_key" | sed -e 's/&/\&amp;/g' -e 's/"/\&quot;/g' -e "s/'/\\&apos;/g" -e 's/</\&lt;/g' -e 's/>/\&gt;/g')"
  prefs_xml="$(cat <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name='apiUrl'>https://api.bitdrift.dev</string>
    <string name='api_key'>$escaped_api_key</string>
</map>
EOF
)"

  printf '%s' "$prefs_xml" | adb -s "$emulator_serial" shell "run-as io.bitdrift.gradletestapp sh -c 'mkdir -p shared_prefs && cat > $shared_prefs_path'"
}

curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"
export MAESTRO_CLI_NO_ANALYTICS=1
export ANDROID_SERIAL="$emulator_serial"

adb start-server
wait_for_emulator_ready
adb -s "$emulator_serial" install -r "$apk_path"
adb -s "$emulator_serial" shell input keyevent 82 >/dev/null 2>&1 || true
seed_gradle_test_app_settings

for attempt in $(seq 1 "$maestro_retries"); do
  echo "Running Maestro attempt $attempt/$maestro_retries"

  if maestro test -e ITERATIONS="$iterations" "$maestro_flow"; then
    exit 0
  fi

  if [[ "$attempt" -eq "$maestro_retries" ]]; then
    exit 1
  fi

  echo "Maestro failed, restarting adb before retry"
  adb kill-server || true
  adb start-server
  wait_for_emulator_ready
  seed_gradle_test_app_settings
done
