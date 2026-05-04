#!/bin/bash
set -euo pipefail

# Resolves an available simulator for a given platform.
# Picks the first device from the newest available runtime.
#
# Usage: ./ci/resolve-ios-simulator.sh [platform]
#   platform: iOS (default), tvOS, watchOS, visionOS
#
# Output: prints IOS_SIMULATOR_DEVICE and IOS_SIMULATOR_VERSION to stdout.
# When run in GitHub Actions (GITHUB_ENV is set), also exports them to
# the workflow environment.

PLATFORM="${1:-iOS}"

DEVICE_JSON=$(xcrun simctl list devices "$PLATFORM" available -j \
  | jq -c '.devices | to_entries | sort_by(.key) | reverse
      | map(select(.value | length > 0)) | first
      | {runtime: .key, name: .value[0].name}')

DEVICE_NAME=$(echo "$DEVICE_JSON" | jq -r '.name')
OS_VERSION=$(echo "$DEVICE_JSON" | jq -r '.runtime | split("-") | .[1:] | join(".")')

if [[ -z "$DEVICE_NAME" || "$DEVICE_NAME" == "null" ]]; then
  echo "Error: no available $PLATFORM simulator found" >&2
  exit 1
fi

echo "IOS_SIMULATOR_DEVICE=$DEVICE_NAME"
echo "IOS_SIMULATOR_VERSION=$OS_VERSION"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "IOS_SIMULATOR_DEVICE=$DEVICE_NAME" >> "$GITHUB_ENV"
  echo "IOS_SIMULATOR_VERSION=$OS_VERSION" >> "$GITHUB_ENV"
fi
