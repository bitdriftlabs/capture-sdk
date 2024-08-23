#!/bin/bash

set -e

# Install flatc
if ! flatc --version &> /dev/null; then
  if [[ -z "$RUNNER_TEMP" ]]; then
    echo "Not running in GHA. Install flatc in your path"
    exit 1
  fi

  FLATC_VERSION=23.5.26
  pushd .
  cd "$RUNNER_TEMP"
  curl -Lfs -o Linux.flatc.binary.clang++-12.zip https://github.com/google/flatbuffers/releases/download/v"${FLATC_VERSION}"/Linux.flatc.binary.clang++-12.zip
  sudo unzip Linux.flatc.binary.clang++-12.zip
  sudo mv flatc /usr/local/bin/flatc
  sudo chmod +x /usr/local/bin/flatc
  popd
fi