#!/bin/bash

# Run this to swap all of the deps to a local version for easy development.
for crate in bd-client-common bd-device bd-events bd-grpc bd-grpc-codec bd-hyper-network bd-key-value \
  bd-log bd-logger bd-matcher bd-metadata bd-panic bd-pgv bd-profile bd-proto bd-proto-util \
  bd-resource-utilization bd-rt bd-runtime-config bd-server-stats bd-session bd-session-replay \
  bd-shutdown bd-test-helpers bd-time; do
  /usr/bin/sed -i '' "s/\(${crate}\)[[:space:]]*=.*/\\1\.path = \"\.\.\/\.\.\/\.\.\/shared-core\/\\1\"/g" packages/core/Cargo.toml
done
