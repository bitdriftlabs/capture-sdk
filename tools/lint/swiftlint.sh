#!/bin/bash

set -euo pipefail

readonly binary="$PWD/external/SwiftLint/swiftlint"
cd "$BUILD_WORKSPACE_DIRECTORY"

"$binary" --quiet "$@"
