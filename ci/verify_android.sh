#!/bin/bash

set -exuo pipefail

adb uninstall io.bitdrift.capture.helloworld || true
adb install android_app.apk
adb shell am start -n io.bitdrift.capture.helloworld/.MainActivity

timeout_seconds=30
elapsed=0
while ! adb logcat -d | grep -q -e 'Capture SDK properly initialized'; do
  if [ "$elapsed" -ge "$timeout_seconds" ]; then
    echo "Timeout after ${timeout_seconds}s waiting for Capture SDK init log"
    exit 1
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done
