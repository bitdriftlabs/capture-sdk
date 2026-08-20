#!/bin/bash

set -euo pipefail

readonly android_sdk_version="4333796"
readonly android_sdk_license_hash="24333f8a63b6825ea9c5514f83c2829b004d1fee"
readonly cmdline_tools_version="6.0"
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_root
# shellcheck source=tools/android_toolchain_versions.sh
source "$script_root/android_toolchain_versions.sh"
readonly ndk_version="$android_ndk_version"
repo_root="$(cd "$script_root/.." && pwd)"
readonly repo_root
readonly android_sdk_root_dir="$HOME/.androidbin/bitdrift-android-sdk"
readonly android_sdk_unarchived_dir="$android_sdk_root_dir/android-sdk-$android_sdk_version-unarchived"
readonly softlink_root_dir="/tmp/bitdrift-android-sdk"
readonly softlink_unarchived_dir="$softlink_root_dir/android-sdk-$android_sdk_version-unarchived"

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
  "platforms;android-$android_sdk_api_level"
  "build-tools;$android_build_tools_version"
)

function download_android_sdk() {
  local -r file="$1"
  local -r unarchive_dir="$2"

  curl -o "$file" --silent --fail "$android_sdk_file_url"
  if ! echo "$android_sdk_file_sha256  $file" | shasum --check --status; then
    echo "Android SDK download sha mismatch" >&2
    exit 1
  fi

  rm -rf "$unarchive_dir"
  mkdir -p "$unarchive_dir"
  unzip -q "$file" -d "$unarchive_dir"
  rm -f "$file"
}

function accept_licenses() {
  mkdir -p "$android_sdk_unarchived_dir/licenses"
  echo "$android_sdk_license_hash" > "$android_sdk_unarchived_dir/licenses/android-sdk-license"
}

function provision_android_sdk_packages() {
  local -r cached_sdkmanager="$android_sdk_unarchived_dir/cmdline-tools/latest/bin/sdkmanager"
  local -r packages_installed=(
    "$android_sdk_unarchived_dir/platform-tools"
    "$android_sdk_unarchived_dir/ndk/$ndk_version"
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

    if [[ -x "$cached_sdkmanager" ]]; then
      ANDROID_HOME="$android_sdk_unarchived_dir" "$repo_root/ci/jdk_wrapper.sh" "$cached_sdkmanager" "--install" \
        "platform-tools" "ndk;$ndk_version" "platforms;android-$android_sdk_api_level" "build-tools;$android_build_tools_version" | (grep -v = || true)
    else
      ANDROID_HOME="$android_sdk_unarchived_dir" "$repo_root/ci/jdk_wrapper.sh" "${install_android_cmd_line_tools[@]}" | (grep -v = || true)
      ANDROID_HOME="$android_sdk_unarchived_dir" "$repo_root/ci/jdk_wrapper.sh" "${install_android_sdk_packages_command[@]}" | (grep -v = || true)
    fi
  fi
}

function expose_latest_cmdline_tools() {
  local -r versioned_cmdline_tools="$android_sdk_unarchived_dir/cmdline-tools/$cmdline_tools_version"
  local -r latest_cmdline_tools="$android_sdk_unarchived_dir/cmdline-tools/latest"

  if [[ -d "$versioned_cmdline_tools" && ! -e "$latest_cmdline_tools" ]]; then
    ln -s "$cmdline_tools_version" "$latest_cmdline_tools"
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

provision_android_sdk_packages
expose_latest_cmdline_tools

if [[ -n "${ANDROID_HOME_ENV_FILE:-}" ]]; then
  echo "$softlink_unarchived_dir" > "$ANDROID_HOME_ENV_FILE"
fi
if [[ -n "${ANDROID_NDK_HOME_ENV_FILE:-}" ]]; then
  echo "$softlink_unarchived_dir/ndk/$ndk_version/" > "$ANDROID_NDK_HOME_ENV_FILE"
fi
if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "ANDROID_HOME=$softlink_unarchived_dir"
    echo "ANDROID_SDK_ROOT=$softlink_unarchived_dir"
    echo "ANDROID_NDK_HOME=$softlink_unarchived_dir/ndk/$ndk_version/"
  } >> "$GITHUB_ENV"
fi
