#!/bin/bash

set -e

python3 ./scripts/license_header.py

# Check if git repository is dirty
if [[ -n $(git status -uno --porcelain) ]]; then
  echo "Error: Git repository is dirty. Run scripts/license_header.sh to update license headers."
  git status -uno --porcelain
  exit 1
fi
