#!/bin/bash

# Checks whether the files in provided regex via the command line has changed when comparing the HEAD ref and
# $GITHUB_BASE_REF, i.e. the target branch (usually main). Returns true if the current branch is main.
#
# Usage: ./ci/files_changed.sh <regex>

set -e

# Trap to handle unexpected errors and log them
trap 'echo "An unexpected error occurred during file change check."; echo "check_result=1" >> "$GITHUB_OUTPUT"; exit 1' ERR

# Check for file changes
if git rev-parse --abbrev-ref HEAD | grep -q ^main$ || git diff --name-only "origin/$GITHUB_BASE_REF" | grep -E "$1" ; then
  echo "Relevant file changes detected!"
  echo "check_result=0" >> "$GITHUB_OUTPUT"
  exit 0  # Relevant changes detected
else
  echo "No relevant changes found."
  echo "check_result=2" >> "$GITHUB_OUTPUT"
fi
