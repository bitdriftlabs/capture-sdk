#!/usr/bin/env bash

set -euo pipefail

version="$("$1")"
if [[ ! "$version" =~ ^[1-9][0-9]*$ ]]; then
  printf 'Expected config_version to print a positive integer, got %q\n' "$version" >&2
  exit 1
fi
