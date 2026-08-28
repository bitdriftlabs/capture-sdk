#!/bin/bash

set -euo pipefail

readonly android_sdk_license_hash="24333f8a63b6825ea9c5514f83c2829b004d1fee"
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_root
# shellcheck source=tools/android_toolchain_versions.sh
source "$script_root/android_toolchain_versions.sh"
readonly android_sdk_root_dir="$HOME/.androidbin/bitdrift-android-sdk"
readonly android_sdk_unarchived_dir="$android_sdk_root_dir/android-sdk-unarchived"
readonly softlink_root_dir="/tmp/bitdrift-android-sdk"
readonly softlink_unarchived_dir="$softlink_root_dir/android-sdk-unarchived"

if [[ "$OSTYPE" == darwin* ]]; then
  readonly android_sdk_file_url="https://dl.google.com/android/repository/commandlinetools-mac-${android_cmdline_tools_build}_latest.zip"
  readonly android_sdk_file_sha256="5673201e6f3869f418eeed3b5cb6c4be7401502bd0aae1b12a29d164d647a54e"
elif [[ "$OSTYPE" == linux-gnu ]]; then
  readonly android_sdk_file_url="https://dl.google.com/android/repository/commandlinetools-linux-${android_cmdline_tools_build}_latest.zip"
  readonly android_sdk_file_sha256="7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee"
else
  echo "Android SDK setup doesn't support this OS: $OSTYPE" >&2
  exit 1
fi

readonly sdkmanager="$android_sdk_unarchived_dir/cmdline-tools/$android_cmdline_tools_version/bin/sdkmanager"

# $1 — Path to file to download to.
# $2 - Directory to unarchive to.
function download_android_sdk() {
  local -r file="$1"
  local -r unarchive_dir="$2"

  curl -o "$file" --silent --fail "$android_sdk_file_url"
  if ! echo "$android_sdk_file_sha256  $file" | shasum --check --status; then
    echo "Android SDK download sha mismatch" >&2
    exit 1
  fi

  # The archive holds a single top-level `cmdline-tools` directory. sdkmanager derives the SDK root
  # from its own location, so it has to land under <sdk>/cmdline-tools/<version>.
  local -r staging_dir="$(mktemp -d)"
  unzip -q "$file" -d "$staging_dir"
  rm -rf "$unarchive_dir"
  mkdir -p "$unarchive_dir/cmdline-tools"
  mv "$staging_dir/cmdline-tools" "$unarchive_dir/cmdline-tools/$android_cmdline_tools_version"
  rm -rf "$staging_dir"
  rm -f "$file"
}

function accept_licenses() {
  mkdir -p "$android_sdk_unarchived_dir/licenses"
  echo "$android_sdk_license_hash" > "$android_sdk_unarchived_dir/licenses/android-sdk-license"
}

# Gradle and AGP expect the canonical <sdk>/cmdline-tools/latest. Nothing here resolves through it;
# every lookup in this repo goes via $android_cmdline_tools_version.
function expose_latest_cmdline_tools() {
  local -r versioned_cmdline_tools="$android_sdk_unarchived_dir/cmdline-tools/$android_cmdline_tools_version"
  local -r latest_cmdline_tools="$android_sdk_unarchived_dir/cmdline-tools/latest"

  if [[ -d "$versioned_cmdline_tools" && ! -e "$latest_cmdline_tools" ]]; then
    ln -s "$android_cmdline_tools_version" "$latest_cmdline_tools"
  fi
}

function provision_android_sdk_packages() {
  local -r packages_installed=(
    "$android_sdk_unarchived_dir/platform-tools"
    "$android_sdk_unarchived_dir/platforms/android-$android_sdk_api_level"
    "$android_sdk_unarchived_dir/build-tools/$android_build_tools_version"
  )
  local package_path

  for package_path in "${packages_installed[@]}"; do
    if [[ ! -d "$package_path" ]]; then
      break
    fi
  done

  if [[ ! -d "$package_path" ]]; then
    accept_licenses

    ANDROID_HOME="$android_sdk_unarchived_dir" "$sdkmanager" "--install" \
      "platform-tools" "platforms;android-$android_sdk_api_level" "build-tools;$android_build_tools_version" | (grep -v = || true)
  fi
}

# Keyed on the versioned cmdline-tools directory, not the SDK root: the root name no longer carries
# the build number, so this is what makes a version bump reinstall rather than silently reuse.
if [[ ! -d "$android_sdk_unarchived_dir/cmdline-tools/$android_cmdline_tools_version" ]]; then
  mkdir -p "$android_sdk_root_dir"
  download_android_sdk "$(mktemp)" "$android_sdk_unarchived_dir"
fi

if [[ ! -d "$softlink_unarchived_dir" ]]; then
  mkdir -p "$softlink_root_dir"
  ln -s "$android_sdk_unarchived_dir" "$softlink_unarchived_dir"
fi

expose_latest_cmdline_tools
provision_android_sdk_packages

if [[ -n "${ANDROID_HOME_ENV_FILE:-}" ]]; then
  echo "$softlink_unarchived_dir" > "$ANDROID_HOME_ENV_FILE"
fi

# Point the rest of the job at this SDK rather than the runner's preinstalled one.
if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "ANDROID_HOME=$softlink_unarchived_dir"
    echo "ANDROID_SDK_ROOT=$softlink_unarchived_dir"
  } >> "$GITHUB_ENV"
fi
