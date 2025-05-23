#!/bin/bash

set -eo pipefail

# Check if GITHUB_BASE_REF is set (i.e., you're in a pull request)
if [ -n "$GITHUB_BASE_REF" ]; then
  git fetch origin "$GITHUB_BASE_REF":"$GITHUB_BASE_REF"
  base_sha=$(git rev-parse "$GITHUB_BASE_REF")
else
  echo "Not in a pull request, skipping base ref fetch."
  base_sha=$(git rev-parse HEAD~1)
fi

# Get the latest commit SHA for the PR branch (the head ref in the forked repository)
final_revision=$GITHUB_SHA

# Use git merge-base to find the common ancestor of the two commits
previous_revision=$(git merge-base "$base_sha" "$final_revision")

# If the only file that changed was .sdk_version, we don't need to run bazel-diff and just mark it as no changes detected.
files_changed=$(git diff --name-only "$previous_revision".."$final_revision")

echo "$(echo "$files_changed" | wc -l) files changed between $previous_revision and $final_revision (truncated):"
echo "$files_changed" | head -n 5

if echo "$files_changed" | grep -q "platform/shared/.sdk_version" && [ "$(echo "$files_changed" | wc -l)" -eq 1 ]; then
  exit 0
fi

exit 1
