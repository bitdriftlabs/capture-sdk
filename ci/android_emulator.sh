#!/usr/bin/env bash

# Android can report sys.boot_completed before first-boot services are available on API 23, so
# require both boot-complete properties, a stopped boot animation, and responsive package manager
# and settings services from the same system_server for several consecutive checks.
wait_for_android_emulator_ready() {
  local serial="${1:-${ANDROID_SERIAL:-emulator-5554}}"
  local attempts="${2:-45}"
  local required_stable_checks="${3:-3}"

  adb -s "$serial" wait-for-device

  local sys_boot_completed
  local dev_boot_completed
  local boot_anim
  local system_server_pid
  local stable_system_server_pid=""
  local stable_checks=0

  for _ in $(seq 1 "$attempts"); do
    sys_boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    dev_boot_completed="$(adb -s "$serial" shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r')"
    boot_anim="$(adb -s "$serial" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"
    system_server_pid="$(adb -s "$serial" shell pidof system_server 2>/dev/null | tr -d '\r')"
    if [[ ! "$system_server_pid" =~ ^[0-9]+$ ]]; then
      system_server_pid="$(adb -s "$serial" shell ps 2>/dev/null | tr -d '\r' |
        awk '$NF == "system_server" { print $2 }')"
    fi

    if [[ "$sys_boot_completed" == "1" ]] &&
      [[ "$dev_boot_completed" == "1" ]] &&
      [[ -z "$boot_anim" || "$boot_anim" == "stopped" ]] &&
      [[ -n "$system_server_pid" ]] &&
      adb -s "$serial" shell cmd package list packages >/dev/null 2>&1 &&
      adb -s "$serial" shell settings get global device_provisioned >/dev/null 2>&1; then
      if [[ "$system_server_pid" == "$stable_system_server_pid" ]]; then
        stable_checks=$((stable_checks + 1))
      else
        stable_system_server_pid="$system_server_pid"
        stable_checks=1
      fi
      if [[ "$stable_checks" -ge "$required_stable_checks" ]]; then
        return 0
      fi
    else
      stable_checks=0
    fi

    adb reconnect offline >/dev/null 2>&1 || true
    sleep 2
  done

  echo "Timed out waiting for emulator system services readiness" \
    "(sys.boot_completed=$sys_boot_completed dev.bootcomplete=$dev_boot_completed init.svc.bootanim=$boot_anim" \
    "system_server=$system_server_pid stable_checks=$stable_checks)"
  return 1
}
