#!/bin/bash

set -e

python3 ./ci/license_header.py

# Check if git repository is dirty
if [[ -n $(git status -uno --porcelain) ]]; then
  echo "Error: Git repository is dirty. Run ci/license_header.py to update license headers."
  git status -uno --porcelain
  exit 1
fi
