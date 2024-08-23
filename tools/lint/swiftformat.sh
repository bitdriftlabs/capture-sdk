#!/bin/bash

set -euo pipefail

readonly binary="$PWD/external/SwiftFormat/swiftformat"
readonly config="$PWD/tools/lint/swiftformat.txt"
cd "$BUILD_WORKSPACE_DIRECTORY"

# TODO(Augustyniak): figure out how to exclude undesired directories as opposed to listing desired ones.
# I could not make `--exlude external`` work for me due to as swiftformat visits parent directories
# https://github.com/nicklockwood/SwiftFormat/issues/426.
"$binary" "$@" platform --config "$config" \
&& "$binary" "$@" test/platform --config "$config" \
&& "$binary" "$@" examples --config "$config"
