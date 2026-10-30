#!/bin/bash

set -euo pipefail

version="$1"
endpoint="repos/$GITHUB_REPOSITORY/contents/platform/shared/.sdk_version"
content="$(printf '%s' "$version" | base64 | tr -d '\n')"
version_sha="$(printf '%s' "$version" | git hash-object --stdin)"

for attempt in 1 2 3; do
  sha="$(gh api --method GET "$endpoint" -f ref=main -q .sha)"
  if [[ "$sha" == "$version_sha" ]]; then
    exit 0
  fi
  if gh api --method PUT "$endpoint" \
    -f message="Update SDK version to $version" \
    -f content="$content" \
    -f sha="$sha" \
    -f branch=main; then
    exit 0
  fi
  echo "Version update attempt $attempt failed; refreshing main." >&2
done

echo "Unable to update the SDK version on main after 3 attempts." >&2
exit 1
