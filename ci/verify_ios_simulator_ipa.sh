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
# shellcheck source=ci/ios_ci_simulator.sh
source "$(dirname "${BASH_SOURCE[0]}")/ios_ci_simulator.sh"
simulator="$(create_ios_ci_simulator)"
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b
xcrun simctl install "$simulator" "$app"
xcrun simctl launch "$simulator" "$bundle_id"
