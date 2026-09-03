#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_dir
readonly maestro_bin="${MAESTRO_BIN:?Set MAESTRO_BIN to the bitdrift-maestro executable.}"
readonly apple_team_id="${APPLE_TEAM_ID:?Set APPLE_TEAM_ID or add it to .bazelrc.local.}"
readonly ios_device_id="${IOS_DEVICE_ID:?Set IOS_DEVICE_ID to the CoreDevice ID of the connected iPhone. Run make list-ios-devices to list available devices.}"
readonly ipa_path="${IOS_PHYSICAL_SMOKE_APP_IPA:?Set IOS_PHYSICAL_SMOKE_APP_IPA to the signed Hello World IPA.}"
readonly flow_path="$script_dir/ios-physical-smoke.yaml"

if [[ ! -f "$ipa_path" ]]; then
  echo "Hello World IPA does not exist: $ipa_path" >&2
  exit 1
fi

hardware_udid="$(python3 "$script_dir/resolve_ios_hardware_udid.py" "$ios_device_id")"
readonly hardware_udid
xcrun devicectl device install app --device "$ios_device_id" "$ipa_path"

"$maestro_bin" --device "$hardware_udid" test \
  --apple-team-id "$apple_team_id" \
  "$flow_path"
