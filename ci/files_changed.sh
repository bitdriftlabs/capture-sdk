#!/bin/bash

# Checks whether the files in provided regex via the command line has changed when comparing the HEAD ref and
# $GITHUB_BASE_REF, i.e. the target branch (usually main). Returns true if the current branch is main.
#
# Usage: ./ci/files_changed.sh <regex>

set -euo pipefail

# Trap to handle unexpected errors and log them
trap 'echo "An unexpected error occurred during file change check."; echo "check_result=1" >> "$GITHUB_OUTPUT"; exit 1' ERR

# Determine the base ref or fallback to HEAD~1 when running on main
if [[ -z "${GITHUB_BASE_REF:-}" ]]; then
  echo "GITHUB_BASE_REF is empty, likely running on main branch. Using HEAD~1 for comparison."
  base_ref="HEAD~1"
else
  base_ref="origin/$GITHUB_BASE_REF"
fi

if git rev-parse --abbrev-ref HEAD | grep -q ^main$ ; then
  echo "Relevant file changes detected!"
  echo "check_result=0" >> "$GITHUB_OUTPUT"
  exit 0
fi

# Run git diff and store output
diff_output=$(git diff --name-only "$base_ref" || exit 1)  # Ensure git diff failures are caught

# Check for relevant file changes
if echo "$diff_output" | grep -E "$1" ; then
  echo "Relevant file changes detected!"
  echo "check_result=0" >> "$GITHUB_OUTPUT"
  exit 0
else
  echo "No relevant changes found."
  echo "check_result=2" >> "$GITHUB_OUTPUT"
fi
