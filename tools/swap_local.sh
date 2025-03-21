#!/bin/bash

# Run this to swap all of the deps to a local version for easy development.
grep bd- Cargo.toml | cut -d' ' -f1 | while read -r crate; do
  /usr/bin/sed -i '' "s/\(${crate}\)[[:space:]]*=.*/\\1\.path = \"\.\.\/shared-core\/\\1\"/g" Cargo.toml
done
