#!/bin/bash

set -euo pipefail

echo "DEBUG: Starting check_bazel.sh"
echo "DEBUG: Current directory: $(pwd)"
echo "DEBUG: GITHUB_BASE_REF: $GITHUB_BASE_REF"
echo "DEBUG: GITHUB_HEAD_REF: $GITHUB_HEAD_REF"
echo "DEBUG: Remote repositories: $(git remote -v)"

# Compares $GITHUB_HEAD_REF and $GITHUB_BASE_REF (PR branch + target branch, usually main) to 
# determine which Bazel targets have changed. This is done by analyzing the cache keys and
# should be authoritative assuming the builds are hermetic.
#
# Usage ./ci/check_bazel.sh <list of targets to check for in the changeset>

# Path to your Bazel WORKSPACE directory
workspace_path=$(pwd)
# Path to your Bazel executable
bazel_path=$(pwd)/bazelw
# Find the remote associated with the base branch (GITHUB_BASE_REF)
remote_name=$(git remote -v | grep fetch | awk '{print $1}' | head -n 1)
# Fetch the base branch if not present
echo "DEBUG: Fetching base branch: $remote_name/$GITHUB_BASE_REF"
git fetch "$remote_name" "$GITHUB_BASE_REF"
# Starting Revision SHA. We use the merge-base to better handle the case where HEAD is not ahead of main.
base_sha=$(git rev-parse "$remote_name/$GITHUB_BASE_REF")
previous_revision=$(git merge-base "$base_sha" "$remote_name/$GITHUB_HEAD_REF")
# Final Revision SHA
final_revision=$GITHUB_HEAD_REF

echo "DEBUG: base_sha: $base_sha"
echo "DEBUG: previous_revision: $previous_revision"
echo "DEBUG: final_revision: $final_revision"

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

for pattern in "$@"
do
  if pattern_impacted "$pattern"; then
    echo "$pattern changed!"
    exit 0
  fi
done

# No relevant changes detected via Bazel.
echo "Nothing changed"
exit 1
