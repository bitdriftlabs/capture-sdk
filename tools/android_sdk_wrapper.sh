#!/bin/bash

set -euo pipefail


readonly android_sdk_version="4333796"
readonly android_sdk_license_hash="24333f8a63b6825ea9c5514f83c2829b004d1fee"

if [[ "$OSTYPE" == darwin* ]]; then
  readonly android_sdk_file_url="https://dl.google.com/android/repository/sdk-tools-darwin-$android_sdk_version.zip"
  readonly android_sdk_file_sha256="ecb29358bc0f13d7c2fa0f9290135a5b608e38434aad9bf7067d0252c160853e"
elif [[ "$OSTYPE" == linux-gnu ]]; then
  readonly android_sdk_file_url="https://dl.google.com/android/repository/sdk-tools-linux-$android_sdk_version.zip"
  readonly android_sdk_file_sha256="92ffee5a1d98d856634e8b71132e8a95d96c83a63fde1099be3d86df3106def9"
else
  echo "Android SDK setup doesn't support this OS: $OSTYPE" >&2
  exit 1
fi

readonly android_sdk_root_dir="$HOME/.androidbin/bitdrift-android-sdk"
readonly android_sdk_unarchived_dir="$android_sdk_root_dir/android-sdk-$android_sdk_version-unarchived"
readonly softlink_root_dir="/tmp/bitdrift-android-sdk"
readonly softlink_unarchived_dir="$softlink_root_dir/android-sdk-$android_sdk_version-unarchived"

readonly cmdline_tools_version="6.0"
readonly ndk_version="27.2.12479018"
readonly install_android_cmd_line_tools=(
  "$android_sdk_unarchived_dir/tools/bin/sdkmanager"
  "--install"
  "cmdline-tools;$cmdline_tools_version"
)
readonly install_android_sdk_packages_command=(
  "$android_sdk_unarchived_dir/cmdline-tools/$cmdline_tools_version/bin/sdkmanager"
  "--install"
  "platform-tools"
  "ndk;$ndk_version"
  "platforms;android-34"
  "build-tools;34.0.0"
)

function download_android_sdk() {
  local -r file="$1"
  local -r unarchive_dir="$2"

  curl -o "$file" --silent --fail "$android_sdk_file_url"
  if ! echo "$android_sdk_file_sha256  $file" | shasum --check --status; then
    echo "error: android SDK download sha mismatch" >&2
    exit 1
  fi

  rm -rf "$unarchive_dir"
  mkdir -p "$unarchive_dir"
  unzip -q "$file" -d "$unarchive_dir"
  rm -f "$file"
}

function accept_licenses() {
  # sdkmanager fails downloads without license(s) acceptance.
  mkdir -p "$android_sdk_unarchived_dir/licenses"
  echo "$android_sdk_license_hash" > "$android_sdk_unarchived_dir/licenses/android-sdk-license"
}

function provision_android_sdk_packages() {
  local -r install_cmd=("${install_android_cmd_line_tools[@]}" "${install_android_sdk_packages_command[@]}")
  local -r install_android_sdk_packages_command_sha256=$(echo "${install_cmd[@]}" | shasum -a 256 | cut -d " " -f1)
  local -r android_sdk_packages_cache_file="$android_sdk_unarchived_dir/$install_android_sdk_packages_command_sha256-installed-marker"

  if [[ ! -f "$android_sdk_packages_cache_file" ]]; then
    accept_licenses;

    ANDROID_HOME="$android_sdk_unarchived_dir" ./ci/jdk_wrapper.sh "${install_android_cmd_line_tools[@]}" | (grep -v = || true)
    ANDROID_HOME="$android_sdk_unarchived_dir" ./ci/jdk_wrapper.sh "${install_android_sdk_packages_command[@]}" | (grep -v = || true)

    touch "$android_sdk_packages_cache_file"
  fi
}

if [[ ! -d "$android_sdk_unarchived_dir" ]]; then
  mkdir -p "$android_sdk_root_dir"
  download_android_sdk "$(mktemp)" "$android_sdk_unarchived_dir"
fi

if [[ ! -d "$softlink_unarchived_dir" ]]; then
  mkdir -p "$softlink_root_dir"
  ln -s "$android_sdk_unarchived_dir" "$softlink_unarchived_dir"
fi

provision_android_sdk_packages;

if [[ -n "${ANDROID_HOME_ENV_FILE:-}" ]]; then
  echo "$softlink_unarchived_dir" > "$ANDROID_HOME_ENV_FILE"
fi
if [[ -n "${ANDROID_NDK_HOME_ENV_FILE:-}" ]]; then
  echo "$softlink_unarchived_dir/ndk/$ndk_version/" > "$ANDROID_NDK_HOME_ENV_FILE"
fi

readonly custom_android_home=$softlink_unarchived_dir
readonly custom_android_ndk_home="$softlink_unarchived_dir/ndk/$ndk_version/"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "ANDROID_NDK_HOME=$custom_android_ndk_home" >> "$GITHUB_ENV"
fi

unset ANDROID_SDK_ROOT

ANDROID_HOME=$custom_android_home \
  ANDROID_NDK_HOME=$custom_android_ndk_home \
  PATH="$custom_android_home/tools/bin:$custom_android_home/platform-tools:$PATH" \
  "$@"
