#!/bin/bash

set -euo pipefail

if [[ $OSTYPE == darwin* ]]; then
  readonly drstring="$PWD/external/_main~_repo_rules~DrString/drstring"
else
  readonly drstring="$PWD/external/_main~_repo_rules~DrString_Linux/usr/bin/drstring"
fi

cd "$BUILD_WORKSPACE_DIRECTORY"

"$drstring" "$@"
