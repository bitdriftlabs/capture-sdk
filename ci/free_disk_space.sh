#!/bin/bash

set -euxo pipefail

# Refer to https://github.com/ReactiveCircus/android-emulator-runner/issues/377#issuecomment-2296679727 for more details.

rm -rf "$AGENT_TOOLSDIRECTORY"
rm -rf /usr/share/dotnet
rm -rf /opt/ghc...

# Disable swap to make it possible to clean up swap strorage.
swapoff -a
rm -f /mnt/swapfile