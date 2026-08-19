#!/bin/bash

set -euo pipefail

# Compares the head ref and $GITHUB_BASE_REF (PR branch + target branch, usually main) to
# determine which Bazel targets have changed. This is done by analyzing the cache keys and
# should be authoritative assuming the builds are hermetic.
#
# Usage ./ci/check_bazel.sh <list of targets to check for in the changeset>

# Trap to handle unexpected errors and log them
trap 'echo "An unexpected error occurred during Bazel check."; echo "check_result=1" >> "$GITHUB_OUTPUT"; exit 1' ERR

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

# Path to your Bazel WORKSPACE directory
workspace_path=$(pwd)
# Path to your Bazel executable
bazel_path=$(pwd)/bazelw

# Linux CI uses these options to warm the exact Bazel test configuration that
# follows a positive affected-targets result.
bazel_diff_args=()
if [[ -n "${BAZEL_DIFF_ARGS:-}" ]]; then
  read -r -a bazel_diff_args <<< "$BAZEL_DIFF_ARGS"
fi

# If the only file that changed was .sdk_version, we don't need to run bazel-diff and just mark it as no changes detected.
if ./ci/version_only_change.sh; then
  echo "Only change was platform/shared/.sdk-version, no Bazel changes detected."
  echo "check_result=2" >> "$GITHUB_OUTPUT"
  exit 1
fi

starting_hashes_json="/tmp/starting_hashes.json"
final_hashes_json="/tmp/final_hashes.json"
impacted_targets_path="/tmp/impacted_targets.txt"

# Keep Bazel out of the launcher path: `generate-hashes` still invokes Bazel for
# each revision, but downloading the native CLI avoids a third Bazel analysis
# just to materialize a bazel-diff wrapper.
bazel_diff_version="v42.0.0"
bazel_diff_dir="${RUNNER_TEMP:-/tmp}/bazel-diff-$bazel_diff_version"
mkdir -p "$bazel_diff_dir"

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)
    bazel_diff_asset="bazel-diff-rust-linux-amd64"
    bazel_diff_sha256="d7bf5c6f74b582fa54b6a6da5b23f7d0e816a4c71af984645ec2d71aa8c37631"
    bazel_diff_command=("$bazel_diff_dir/$bazel_diff_asset")
    ;;
  Darwin-arm64)
    bazel_diff_asset="bazel-diff-rust-macos-arm64"
    bazel_diff_sha256="f32b8db587cd42eae59da2bb3055bfead9b650cfc0b4565677db10a7770b19c1"
    bazel_diff_command=("$bazel_diff_dir/$bazel_diff_asset")
    ;;
  *)
    # The deploy JAR keeps local use working on hosts without a published
    # native binary, such as Intel macOS.
    bazel_diff_asset="bazel-diff_deploy.jar"
    bazel_diff_sha256="0170b70fe2f24477ab056c11f5c3240d8b45a1dca5ab73ad252c096679c5500c"
    bazel_diff_command=(java -jar "$bazel_diff_dir/$bazel_diff_asset")
    ;;
esac

bazel_diff_path="$bazel_diff_dir/$bazel_diff_asset"
if [[ ! -f "$bazel_diff_path" ]] || [[ "$(shasum -a 256 "$bazel_diff_path" | awk '{print $1}')" != "$bazel_diff_sha256" ]]; then
  curl --fail --location --retry 3 --retry-all-errors --output "$bazel_diff_path" \
    "https://github.com/Tinder/bazel-diff/releases/download/$bazel_diff_version/$bazel_diff_asset"
fi

if [[ "$(shasum -a 256 "$bazel_diff_path" | awk '{print $1}')" != "$bazel_diff_sha256" ]]; then
  echo "bazel-diff checksum verification failed."
  exit 1
fi
chmod +x "$bazel_diff_path"

git -C "$workspace_path" checkout "$previous_revision" --quiet

"${bazel_diff_command[@]}" generate-hashes -w "$workspace_path" -b "$bazel_path" $starting_hashes_json --excludeExternalTargets

git -C "$workspace_path" checkout "$final_revision" --quiet

"${bazel_diff_command[@]}" generate-hashes -w "$workspace_path" -b "$bazel_path" $final_hashes_json --excludeExternalTargets

"${bazel_diff_command[@]}" get-impacted-targets -w "$workspace_path" -sh $starting_hashes_json -fh $final_hashes_json -o $impacted_targets_path

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
  if [[ ${#bazel_diff_args[@]} -gt 0 ]]; then
    "$bazel_path" build --nobuild --build_tests_only "${bazel_diff_args[@]}" "$@"
  fi
  echo "check_result=0" >> "$GITHUB_OUTPUT"
  exit 0  # Changes found
else
  echo "No changes detected."
  echo "check_result=2" >> "$GITHUB_OUTPUT"
  exit 1
fi
