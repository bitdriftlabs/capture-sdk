#!/bin/bash

set -euo pipefail

echo "+++ Building .so"

CC=$(which clang) CXX=$(which clang++) ./bazelw build \
  --announce_rc \
  --config=ci \
  --config=release-common \
  //platform/shared:capture
