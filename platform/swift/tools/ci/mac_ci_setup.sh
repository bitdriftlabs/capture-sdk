#!/bin/bash

set -euxo pipefail

# https://github.com/actions/runner-images/blob/main/images/macos/macos-14-Readme.md#xcode
sudo xcode-select --switch /Applications/Xcode_16.2.app
