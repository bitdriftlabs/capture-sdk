#!/bin/bash

set -euo pipefail

readonly jdk_version=8.0.302
readonly major_java_version=8

if [[ "$OSTYPE" == "darwin"* ]]; then
  arch="$(uname -m)"
  if [[  "$arch" == arm64 ]]; then
    readonly jdk_file_sha256="4482990c96e87519f52725b0bf3a6171510e3da268d55b793d1bf6eeb6485030"
    readonly jdk_file_url="https://cdn.azul.com/zulu/bin/zulu8.56.0.23-ca-jdk$jdk_version-macosx_aarch64.tar.gz"
  else
    readonly jdk_file_sha256="497c1d6eae5f3943a1c5f74be7bb8a650d6b0dc3bf069973d6d04f45c3daaf88"
    readonly jdk_file_url="https://cdn.azul.com/zulu/bin/zulu8.56.0.21-ca-jdk$jdk_version-macosx_x64.tar.gz"
  fi
elif [[ "$OSTYPE" == "linux-gnu" ]]; then
  readonly jdk_file_sha256="f6e6946713575aeeadfb75bd2eb245669e59ce4f797880490beb53a6c5b7138a"
  readonly jdk_file_url="https://cdn.azul.com/zulu/bin/zulu8.56.0.21-ca-jdk$jdk_version-linux_x64.tar.gz"
else
  echo "JDK 8 setup doesn't support this OS: $OSTYPE" >&2
  exit 1
fi

readonly jdk_root_dir="$HOME/.androidbin/bitdrift-jdk/$jdk_version"
readonly jdk_unarchived_dir="$jdk_root_dir/jdk-unarchived"
readonly softlink_root_dir="/tmp/bitdrift-jdk/$jdk_version"
readonly softlink_unarchived_dir="$softlink_root_dir/jdk-unarchived"
readonly jdk_unpacked_success_marker_file="$jdk_unarchived_dir/unpacked-marker-$jdk_file_sha256"

# $1 — Path to file to download to.
# $2 - Directory to unarchive to.
function download_and_unpack_jdk() {
  local -r file="$1"
  local -r unarchive_dir="$2"

  curl -o "$file" --silent --fail "$jdk_file_url"
  if ! echo "$jdk_file_sha256  $file" | shasum --check --status; then
    echo "error: jdk download sha mismatch" >&2
    exit 1
  fi

  mkdir -p "$unarchive_dir"
  tar -xf "$file" -C "$unarchive_dir" --strip-components=1
  touch "$jdk_unpacked_success_marker_file"
  rm -f "$file"
}

if [[ ! -f "$jdk_unpacked_success_marker_file" ]]; then
  rm -rf "$jdk_unarchived_dir"
  jdk_file=$(mktemp)
  download_and_unpack_jdk "$jdk_file" "$jdk_unarchived_dir"
fi

if [[ ! -d "$softlink_unarchived_dir" ]]; then
  mkdir -p "$softlink_root_dir"
fi

rm -f "$softlink_unarchived_dir"
if [[ "$OSTYPE" == "darwin"* ]]; then
  ln -s "$jdk_unarchived_dir/zulu-$major_java_version.jdk/Contents/Home" "$softlink_unarchived_dir"
elif [[ "$OSTYPE" == "linux-gnu" ]]; then
  ln -s "$jdk_unarchived_dir" "$softlink_unarchived_dir"
else
  echo "JDK wrapper doesn't support this OS: $OSTYPE" >&2
  exit 1
fi

readonly custom_java_home="$softlink_unarchived_dir"
JAVA_HOME="$custom_java_home" \
  "$@"
