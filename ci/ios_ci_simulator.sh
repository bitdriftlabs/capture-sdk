#!/usr/bin/env bash

create_ios_ci_simulator() {
  local runtime
  runtime="$(xcrun simctl list runtimes -j | jq -er '
    [.runtimes[] | select(.isAvailable and (.identifier | startswith("com.apple.CoreSimulator.SimRuntime.iOS-26-")))]
    | max_by(.version | split(".") | map(tonumber)) | .identifier // error("No iOS 26 simulator runtime installed")
  ')" || return
  xcrun simctl create "Capture CI" com.apple.CoreSimulator.SimDeviceType.iPhone-17 "$runtime"
}
