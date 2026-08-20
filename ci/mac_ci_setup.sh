#!/bin/bash

set -e

readonly xcode_version="${1:-26.0.1}"
readonly xcode_app="/Applications/Xcode_$xcode_version.app"

# https://github.com/actions/runner-images/blob/main/images/macos/macos-14-Readme.md#xcode
if [[ ! -d "$xcode_app" ]]; then
  echo "$xcode_app is not installed on this machine. Available Xcode versions:" >&2
  ls -d /Applications/Xcode*.app >&2
  exit 1
fi

sudo xcode-select --switch "$xcode_app"

# workaround for https://github.com/actions/setup-python/issues/577#issuecomment-1365231818
# homebrew fails to update python to 3.9.1.1 due to unlinking failure
rm -f /usr/local/bin/2to3
rm -f /usr/local/bin/2to3-3.11
rm -f /usr/local/bin/idle3
rm -f /usr/local/bin/idle3.11
rm -f /usr/local/bin/pydoc3
rm -f /usr/local/bin/pydoc3.11
rm -f /usr/local/bin/python3
rm -f /usr/local/bin/python3.11
rm -f /usr/local/bin/python3-config
rm -f /usr/local/bin/python3.11-config
