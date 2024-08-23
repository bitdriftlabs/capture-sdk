#!/bin/bash

# Checks whether the files in provided regex via the command line has changed when comparing the HEAD ref and
# $GITHUB_BASE_REF, i.e. the target branch (usually main). Returns true if the current branch is main.
#
# Usage: ./ci/files_changed.sh <regex>

set -e

git rev-parse --abbrev-ref HEAD | grep -q ^main$ || git diff --name-only "origin/$GITHUB_BASE_REF" | grep -E "$1"
