#!/bin/bash

# Checks whether the files in provided regex via the command line have changed when comparing the HEAD ref and
# $GITHUB_BASE_REF, i.e. the target branch (usually main). Returns true if the current branch is main.
#
# Usage: ./ci/files_changed.sh <regex>

set -e

# Get the current branch name
current_branch=$(git rev-parse --abbrev-ref HEAD)

# Find the remote associated with the base branch (GITHUB_BASE_REF)
remote_name=$(git remote -v | grep fetch | awk '{print $1}' | head -n 1)

# If the current branch is "main", exit successfully (no need to check file changes)
if [[ "$current_branch" == "main" ]]; then
  exit 0
fi

# Check if any files matching the regex have changed between the current HEAD and the base branch
git diff --name-only "$remote_name/$GITHUB_BASE_REF" | grep -E "$1"
