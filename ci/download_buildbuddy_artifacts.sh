#!/usr/bin/env bash

set -euo pipefail

invocation_id="${1:?Expected BuildBuddy workflow invocation ID}"
commit="${2:?Expected commit SHA}"
output_dir="${3:?Expected output directory}"
shift 3
: "${1:?Expected at least one artifact name}"
: "${BUILDBUDDY_API_KEY:?Expected BuildBuddy API key}"

# shellcheck source=ci/buildbuddy_api.sh
source "$(dirname "${BASH_SOURCE[0]}")/buildbuddy_api.sh"

request="$(jq -nc --arg id "$invocation_id" '{selector: {invocation_id: $id}, include_artifacts: true}')"
invocation="$(buildbuddy_api GetInvocation --retry 3 --data "$request" | jq -ce \
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
staging_dir="$(mktemp -d "$output_dir/.buildbuddy-artifacts.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT
for name in "$@"; do
  if [[ ! "$name" =~ ^[[:alnum:]][[:alnum:]_.-]*$ ]]; then
    echo "Expected an artifact basename, got $name." >&2
    exit 1
  fi
  uri="$(jq -er --arg name "$name" '
    [.artifacts[] | select((.name | split("/") | last) == $name) | .uri]
    | if length == 1 then .[0] else error("Expected exactly one artifact named " + $name) end
    | select(startswith("bytestream://"))
  ' <<< "$invocation")"
  request="$(jq -nc --arg uri "$uri" '{uri: $uri}')"
  buildbuddy_api GetFile --retry 3 --max-time 600 --data "$request" --output "$staging_dir/$name"
  case "$name" in
    *.zip | *.ipa | *.apk) unzip -tq "$staging_dir/$name" ;;
    *.tar.gz) tar -tzf "$staging_dir/$name" > /dev/null ;;
    *.txt) grep -Eq '^[1-9][0-9]*$' "$staging_dir/$name" ;;
  esac
done
for name in "$@"; do
  mv "$staging_dir/$name" "$output_dir/$name"
done
