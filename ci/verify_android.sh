#!/bin/bash

set -euo pipefail

readonly api_level="$1"
readonly emulator="$PWD/$2"
readonly adb_binary="$PWD/$3"
readonly image_dir="$PWD/${4%/*}"
readonly apk="$PWD/$5"
readonly emulator_port=5564
export ANDROID_SERIAL="emulator-${emulator_port}"
export ANDROID_ADB_SERVER_PORT=5038

# shellcheck source=ci/android_emulator.sh
source "$PWD/ci/android_emulator.sh"

if [[ ! -c /dev/kvm || ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  echo "The Android smoke test requires read/write access to /dev/kvm." >&2
  exit 1
fi

# Keep the AVD, adb keys and emulator state isolated from other SDK installs.
export HOME="$TEST_TMPDIR/home"
export ANDROID_USER_HOME="$HOME/.android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
unset ANDROID_SDK_HOME ADB_SERVER_SOCKET

# The emulator resolves the AVD image through an SDK root with a platform-tools directory.
export ANDROID_HOME="$TEST_TMPDIR/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
readonly image_sysdir="system-images/android-$api_level/google_apis/x86_64"
mkdir -p "$ANDROID_HOME/platform-tools" "$ANDROID_HOME/${image_sysdir%/*}"
ln -s "$adb_binary" "$ANDROID_HOME/platform-tools/adb"
ln -s "$image_dir" "$ANDROID_HOME/$image_sysdir"

readonly avd_name="capture-smoke"
readonly avd_dir="$ANDROID_AVD_HOME/$avd_name.avd"
mkdir -p "$avd_dir"

cat >"$ANDROID_AVD_HOME/$avd_name.ini" <<AVD
avd.ini.encoding=UTF-8
path=$avd_dir
target=android-$api_level
AVD

cat >"$avd_dir/config.ini" <<AVD
AvdId=$avd_name
PlayStore.enabled=false
abi.type=x86_64
disk.dataPartition.size=6144M
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.gpu.enabled=yes
hw.lcd.density=560
hw.lcd.height=2560
hw.lcd.width=1440
hw.ramSize=2048
image.sysdir.1=$image_sysdir/
tag.id=google_apis
target=android-$api_level
AVD

adb() {
  "$adb_binary" -P "$ANDROID_ADB_SERVER_PORT" "$@"
}

emulator_pid=""
emulator_log="${TEST_UNDECLARED_OUTPUTS_DIR:-$TEST_TMPDIR}/emulator.log"
trap '
  status=$?
  if (( status != 0 )) && [[ -f "$emulator_log" ]]; then
    tail -200 "$emulator_log" >&2
  fi
  if [[ -n "$emulator_pid" ]]; then
    kill "$emulator_pid" 2>/dev/null || true
    wait "$emulator_pid" 2>/dev/null || true
  fi
  adb kill-server >/dev/null 2>&1 || true
' EXIT

boot_started=$SECONDS
adb start-server
"$emulator" -avd "$avd_name" -port "$emulator_port" -accel on \
  -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim -no-snapshot \
  -no-metrics -camera-back none >"$emulator_log" 2>&1 &
emulator_pid=$!

# Bound the time spent waiting for adb and report early emulator exit.
for ((attempt = 0; attempt < 90; attempt++)); do
  if adb -s "$ANDROID_SERIAL" get-state 2>/dev/null | grep -qx device; then
    break
  fi
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

if ! adb -s "$ANDROID_SERIAL" get-state 2>/dev/null | grep -qx device; then
  echo "Emulator did not connect to adb." >&2
  exit 1
fi
wait_for_android_emulator_ready "$ANDROID_SERIAL"
printf 'Android API %s booted in %ss\n' "$api_level" "$((SECONDS - boot_started))"

adb install "$apk"
adb shell am start -n io.bitdrift.capture.helloworld/.MainActivity

for ((elapsed = 0; elapsed < 30; elapsed++)); do
  if adb logcat -d | grep -F 'Capture SDK properly initialized' >/dev/null; then
    exit 0
  fi
  sleep 1
done

echo "Timeout after 30s waiting for Capture SDK init log"
echo "Logcat at timeout:"
adb logcat -d -v threadtime || true
exit 1
