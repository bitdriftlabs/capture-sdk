#!/bin/bash

DEFAULT_PATH="../shared-core"
REPO_ROOT_DIR="$(dirname "$(dirname "$(realpath "$0")")")"
CARGO_TOML="$REPO_ROOT_DIR/Cargo.toml"

if [ ! -f "$CARGO_TOML" ]; then
  echo "Error: Cargo.toml not found in the root directory."
  exit 1
fi

if [ -d "$DEFAULT_PATH" ]; then
  CUSTOM_PATH=$DEFAULT_PATH
  echo "Using default path: $CUSTOM_PATH"
else
  if [ -z "$1" ]; then
    echo "Default path ($DEFAULT_PATH) not found."
    echo "Please provide a custom path to shared-core."
    echo "Usage: $0 <path_to_shared_core>"
    exit 1
  else
    CUSTOM_PATH=$1
  fi
fi

# Run this to swap all of the deps to a local version for easy development.
grep bd- "$CARGO_TOML" | cut -d' ' -f1 | while read -r crate; do
  /usr/bin/sed -i '' "s|\(${crate}\)[[:space:]]*=.*|\1.path = \"${CUSTOM_PATH}/\1\"|g" "$CARGO_TOML"
done
