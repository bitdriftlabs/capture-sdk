#!/usr/bin/env bash

set -euo pipefail

ipa="${1:?Expected the simulator IPA path}"
output="$(mktemp -d)"
simulator=""
cleanup() {
  if [[ -n "$simulator" ]]; then
    xcrun simctl delete "$simulator" || true
  fi
  rm -rf "$output"
}
trap cleanup EXIT

ditto -x -k "$ipa" "$output"
app="$output/Payload/Bitdrift Sample App.app"
bundle_id="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")"
runtime="${IOS_CI_SIMULATOR_RUNTIME:-}"
if [[ -z "$runtime" ]]; then
  runtime="$(xcrun simctl list runtimes -j | jq -er '
    [.runtimes[] | select(.isAvailable and (.identifier | startswith("com.apple.CoreSimulator.SimRuntime.iOS-26-")))]
    | max_by(.version | split(".") | map(tonumber)) | .identifier // error("No iOS 26 simulator runtime installed")
  ')"
fi
simulator="$(xcrun simctl create "Capture CI" com.apple.CoreSimulator.SimDeviceType.iPhone-17 "$runtime")"
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b
xcrun simctl install "$simulator" "$app"
xcrun simctl launch "$simulator" "$bundle_id"
