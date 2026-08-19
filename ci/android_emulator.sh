#!/usr/bin/env bash

# Android can report sys.boot_completed before first-boot services are available on API 23, so
# require both boot-complete properties, a stopped boot animation, and a responsive package manager.
wait_for_android_emulator_ready() {
  local serial="${1:-${ANDROID_SERIAL:-emulator-5554}}"

  adb -s "$serial" wait-for-device

  for _ in $(seq 1 45); do
    local sys_boot_completed
    local dev_boot_completed
    local boot_anim

    sys_boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    dev_boot_completed="$(adb -s "$serial" shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r')"
    boot_anim="$(adb -s "$serial" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"

    if [[ "$sys_boot_completed" == "1" ]] &&
      [[ "$dev_boot_completed" == "1" ]] &&
      [[ "$boot_anim" == "stopped" ]] &&
      adb -s "$serial" shell cmd package list packages >/dev/null 2>&1; then
      return 0
    fi

    adb reconnect offline >/dev/null 2>&1 || true
    sleep 2
  done

  echo "Timed out waiting for emulator package manager readiness"
  return 1
}
