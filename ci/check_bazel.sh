#!/bin/bash

set -euo pipefail

# Compares the head ref and $GITHUB_BASE_REF (PR branch + target branch, usually main) to
# determine which Bazel targets have changed. This is done by analyzing the cache keys and
# should be authoritative assuming the builds are hermetic.
#
# Usage ./ci/check_bazel.sh <list of targets to check for in the changeset>

# Trap to handle unexpected errors and log them
trap 'echo "An unexpected error occurred during Bazel check."; echo "check_result=1" >> "$GITHUB_OUTPUT"; exit 1' ERR

# Ensure we fetch the base branch (main) to make it available
git fetch origin "$GITHUB_BASE_REF":"$GITHUB_BASE_REF"

# Get the latest commit SHA for the base branch (target branch of the PR)
base_sha=$(git rev-parse "$GITHUB_BASE_REF")
# Get the latest commit SHA for the PR branch (the head ref in the forked repository)
final_revision=$GITHUB_SHA

# Use git merge-base to find the common ancestor of the two commits
previous_revision=$(git merge-base "$base_sha" "$final_revision")

# Path to your Bazel WORKSPACE directory
workspace_path=$(pwd)
# Path to your Bazel executable
bazel_path=$(pwd)/bazelw

starting_hashes_json="/tmp/starting_hashes.json"
final_hashes_json="/tmp/final_hashes.json"
impacted_targets_path="/tmp/impacted_targets.txt"
bazel_diff="/tmp/bazel_diff"

"$bazel_path" run :bazel-diff --script_path="$bazel_diff"

git -C "$workspace_path" checkout "$previous_revision" --quiet

$bazel_diff generate-hashes -w "$workspace_path" -b "$bazel_path" $starting_hashes_json

git -C "$workspace_path" checkout "$final_revision" --quiet

$bazel_diff generate-hashes -w "$workspace_path" -b "$bazel_path" $final_hashes_json

$bazel_diff get-impacted-targets -sh $starting_hashes_json -fh $final_hashes_json -o $impacted_targets_path

# First pretty print the targets for debugging

impacted_targets=()
IFS=$'\n' read -d '' -r -a impacted_targets < $impacted_targets_path || true
formatted_impacted_targets="$(IFS=$'\n'; echo "${impacted_targets[*]}")"

# Piping the output through to grep is flaky and will cause a broken pipe. Write the contents to a file
# and grep the file to avoid this.
echo "$formatted_impacted_targets" | tee /tmp/impacted_targets.txt

# Look for the patterns provided as arguments to this script. $formatted_impacted_targets contains
# a list of all the Bazel targets impacted by the changes between the two branches, so we just
# check to see if any of the provided patterns appear in the list of targets.

pattern_impacted() {
  grep -q "$1" /tmp/impacted_targets.txt
}

changes_detected=false

for pattern in "$@"
do
  if pattern_impacted "$pattern"; then
    echo "$pattern changed!"
    changes_detected=true
    break
  fi
done

# Exit code based on whether changes were detected
if [ "$changes_detected" = true ]; then
  echo "check_result=0" >> "$GITHUB_OUTPUT"
  exit 0  # Changes found
else
  echo "No changes detected."
  echo "check_result=2" >> "$GITHUB_OUTPUT"
fi
