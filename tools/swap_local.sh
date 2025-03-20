#!/bin/bash

# Run this to swap all of the deps to a local version for easy development.
for crate in $(grep bd- Cargo.toml | cut -d' ' -f1); do
  /usr/bin/sed -i '' "s/\(${crate}\)[[:space:]]*=.*/\\1\.path = \"\.\.\/shared-core\/\\1\"/g" Cargo.toml
done
