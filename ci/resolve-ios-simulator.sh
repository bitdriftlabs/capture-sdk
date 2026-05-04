#!/bin/bash
set -euo pipefail

# Resolves an available simulator device matching the active Xcode's SDK.
#
# Usage: ./ci/resolve-ios-simulator.sh [platform]
#   platform: iOS (default), tvOS, watchOS, visionOS
#
# Output: prints IOS_SIMULATOR_DEVICE and IOS_SIMULATOR_VERSION to stdout.
# When run in GitHub Actions (GITHUB_ENV is set), also exports them to the
# workflow environment so subsequent steps can use them directly.

PLATFORM="${1:-iOS}"

OS_VERSION=$(xcrun --sdk iphonesimulator --show-sdk-version)

DEVICE_NAME=$(xcrun simctl list devices "$PLATFORM" available -j \
  | jq -r --arg version "com.apple.CoreSimulator.SimRuntime.${PLATFORM}-$(echo "$OS_VERSION" | tr '.' '-')" \
      '.devices[$version] // [] | map(select(.isAvailable)) | first | .name // empty')

if [[ -z "$DEVICE_NAME" ]]; then
  echo "Error: no available $PLATFORM $OS_VERSION simulator found (active Xcode SDK: $OS_VERSION)" >&2
  exit 1
fi

echo "IOS_SIMULATOR_DEVICE=$DEVICE_NAME"
echo "IOS_SIMULATOR_VERSION=$OS_VERSION"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "IOS_SIMULATOR_DEVICE=$DEVICE_NAME" >> "$GITHUB_ENV"
  echo "IOS_SIMULATOR_VERSION=$OS_VERSION" >> "$GITHUB_ENV"
fi
