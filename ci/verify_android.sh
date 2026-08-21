#!/bin/bash

set -exuo pipefail

readonly emulator_serial="${ANDROID_SERIAL:-emulator-5554}"

# shellcheck source=ci/android_emulator.sh
source "$(dirname "${BASH_SOURCE[0]}")/android_emulator.sh"

wait_for_android_emulator_ready "$emulator_serial"

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
