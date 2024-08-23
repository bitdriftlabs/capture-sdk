#!/bin/bash

set -exuo pipefail

adb uninstall io.bitdrift.capture.helloworld || true
adb install android_app.apk
adb shell am start -n io.bitdrift.capture.helloworld/.MainActivity

while ! adb logcat -d | grep -q -e 'Capture Logger has been running for 15000 ms'; do
  sleep 1
done
