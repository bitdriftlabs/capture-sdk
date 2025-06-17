#!/bin/bash

set -euo pipefail

# Usage: ./bump-shared-core.sh [sha]
# If sha is not provided, it defaults to the latest commit on the main branch of shared-core.

if ! which cargo-upgrade >/dev/null 2>&1; then
  echo "cargo-edit is not installed. Please install it with 'cargo install cargo-edit' and make sure that it's in your PATH."
  exit 1
fi

shared_core_main=$(git ls-remote git@github.com:bitdriftlabs/shared-core.git main | awk '{print $1}')
sha="${1:-$shared_core_main}"

old_version=$(grep bd-api Cargo.toml | grep -Eo 'rev = ".*"' | cut -d' ' -f3 | tr -d '"')

sed -i "s|\"$old_version\"|\"$sha\"|g" Cargo.toml

cargo upgrade --incompatible
