#!/bin/bash

set -exuo pipefail

readonly emulator_serial="${ANDROID_SERIAL:-emulator-5554}"

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

wait_for_emulator_ready

adb uninstall io.bitdrift.capture.helloworld || true
adb install android_app.apk
adb shell am start -n io.bitdrift.capture.helloworld/.MainActivity

timeout_seconds=30
elapsed=0
while ! adb logcat -d | grep -q -e 'Capture SDK properly initialized'; do
  if [ "$elapsed" -ge "$timeout_seconds" ]; then
    echo "Timeout after ${timeout_seconds}s waiting for Capture SDK init log"
    echo "Logcat at timeout:"
    adb logcat -d -v threadtime || true
    exit 1
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done
