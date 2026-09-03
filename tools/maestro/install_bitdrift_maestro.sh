#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_dir
repo_root="$(cd "$script_dir/../.." && pwd)"
readonly repo_root
readonly version="${BITDRIFT_MAESTRO_VERSION:?Set BITDRIFT_MAESTRO_VERSION to the released Maestro version.}"
readonly install_dir="${BITDRIFT_MAESTRO_DIR:-$repo_root/.tools/bitdrift-maestro/$version}"
readonly maestro_bin="$install_dir/bin/maestro"
readonly archive_url="https://github.com/bitdriftlabs/maestro/releases/download/bd-v$version/bitdrift-maestro.zip"

if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "BITDRIFT_MAESTRO_VERSION must use major.minor.patch form; got '$version'." >&2
  exit 1
fi

if [[ -x "$maestro_bin" ]]; then
  echo "bitdrift-maestro $version is already installed at $maestro_bin"
  exit 0
fi

if [[ -e "$install_dir" ]]; then
  echo "Refusing to overwrite incomplete installation at $install_dir. Remove it and retry." >&2
  exit 1
fi

for command in curl unzip; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 1
  fi
done

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/bitdrift-maestro.XXXXXX")"
readonly temp_dir
trap 'rm -rf "$temp_dir"' EXIT

echo "Downloading bitdrift-maestro $version..."
curl --fail --location --retry 3 --retry-all-errors --output "$temp_dir/bitdrift-maestro.zip" "$archive_url"
unzip -q "$temp_dir/bitdrift-maestro.zip" -d "$temp_dir"

if [[ ! -x "$temp_dir/maestro/bin/maestro" ]]; then
  echo "Release archive did not contain maestro/bin/maestro." >&2
  exit 1
fi

mkdir -p "$(dirname "$install_dir")"
mv "$temp_dir/maestro" "$install_dir"
echo "Installed bitdrift-maestro $version at $maestro_bin"
