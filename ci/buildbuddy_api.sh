#!/usr/bin/env bash

buildbuddy_api() {
  local method="$1"
  shift
  printf 'header = "x-buildbuddy-api-key: %s"\n' "$BUILDBUDDY_API_KEY" |
    curl --config - --fail --silent --show-error --max-time 120 \
      --header 'Content-Type: application/json' \
      "https://app.buildbuddy.io/api/v1/$method" "$@"
}
