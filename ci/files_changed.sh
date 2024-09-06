#!/bin/bash

set -e

# Get the current branch name
current_branch=$(git rev-parse --abbrev-ref HEAD)

# Find the remote associated with the base branch (GITHUB_BASE_REF)
remote_name=$(git remote -v | grep fetch | awk '{print $1}' | head -n 1)

# If the current branch is "main", exit successfully (no need to check file changes)
if [[ "$current_branch" == "main" ]]; then
  exit 0
fi

# Fetch the base branch if it doesn't exist locally
if ! git show-ref --quiet refs/remotes/$remote_name/$GITHUB_BASE_REF; then
  git fetch $remote_name $GITHUB_BASE_REF
fi

# Check if any files matching the regex have changed between the current HEAD and the base branch
git diff --name-only "$remote_name/$GITHUB_BASE_REF" | grep -E "$1"
