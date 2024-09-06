#!/bin/bash

set -e

echo "DEBUG: Starting files_changed.sh"
echo "DEBUG: Current directory: $(pwd)"
echo "DEBUG: Current branch: $(git rev-parse --abbrev-ref HEAD)"
echo "DEBUG: GITHUB_BASE_REF: $GITHUB_BASE_REF"
echo "DEBUG: Remote repositories: $(git remote -v)"

# Get the current branch name
current_branch=$(git rev-parse --abbrev-ref HEAD)

# Find the remote associated with the base branch (GITHUB_BASE_REF)
remote_name=$(git remote -v | grep fetch | awk '{print $1}' | head -n 1)

echo "DEBUG: Remote name: $remote_name"

# If the current branch is "main", exit successfully (no need to check file changes)
if [[ "$current_branch" == "main" ]]; then
  exit 0
fi

# Fetch the base branch if it doesn't exist locally
if ! git show-ref --quiet refs/remotes/$remote_name/$GITHUB_BASE_REF; then
  echo "DEBUG: Fetching base branch: $remote_name/$GITHUB_BASE_REF"
  git fetch $remote_name $GITHUB_BASE_REF
fi

# Check if any files matching the regex have changed between the current HEAD and the base branch
echo "DEBUG: Comparing changes between HEAD and $remote_name/$GITHUB_BASE_REF"
git diff --name-only "$remote_name/$GITHUB_BASE_REF" | grep -E "$1"
