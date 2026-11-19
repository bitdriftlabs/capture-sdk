#!/usr/bin/env bash

set -euo pipefail

invocation_id="${1:?Expected BuildBuddy workflow invocation ID}"
commit="${2:?Expected commit SHA}"
output_dir="${3:?Expected output directory}"
: "${BUILDBUDDY_API_KEY:?Expected BuildBuddy API key}"

api() {
  local method="$1"
  shift
  printf 'header = "x-buildbuddy-api-key: %s"\n' "$BUILDBUDDY_API_KEY" |
    curl --config - --fail --silent --show-error --retry 3 \
      --header 'Content-Type: application/json' \
      "https://app.buildbuddy.io/api/v1/$method" "$@"
}

request="$(jq -nc --arg id "$invocation_id" '{selector: {invocation_id: $id}, include_artifacts: true}')"
invocation="$(api GetInvocation --data "$request" | jq -ce \
  --arg id "$invocation_id" --arg commit "$commit" '
    .invocation[] | select(
      .id.invocationId == $id and .commitSha == $commit and .success == true
      and (.repoUrl | sub("\\.git$"; "")) == "https://github.com/bitdriftlabs/capture-sdk"
    )
  ')" || {
  echo "Expected a successful Capture SDK BuildBuddy workflow for commit $commit." >&2
  exit 1
}

mkdir -p "$output_dir"
staging_dir="$(mktemp -d "$output_dir/.ios-artifacts.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT
for name in Capture.ios.zip ios_app_size.ipa; do
  uri="$(jq -er --arg name "$name" '
    [.artifacts[] | select((.name | split("/") | last) == $name) | .uri]
    | if length == 1 then .[0] else error("Expected exactly one artifact named " + $name) end
    | select(startswith("bytestream://"))
  ' <<< "$invocation")"
  request="$(jq -nc --arg uri "$uri" '{uri: $uri}')"
  api GetFile --data "$request" --output "$staging_dir/$name"
  unzip -tq "$staging_dir/$name"
done
for name in Capture.ios.zip ios_app_size.ipa; do
  mv "$staging_dir/$name" "$output_dir/$name"
done
