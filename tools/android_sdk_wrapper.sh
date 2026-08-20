#!/bin/bash

set -euo pipefail

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_root
readonly android_sdk_version="4333796"
# shellcheck source=tools/android_toolchain_versions.sh
source "$script_root/android_toolchain_versions.sh"
readonly ndk_version="$android_ndk_version"
readonly softlink_root_dir="/tmp/bitdrift-android-sdk"
readonly custom_android_home="$softlink_root_dir/android-sdk-$android_sdk_version-unarchived"
readonly custom_android_ndk_home="$custom_android_home/ndk/$ndk_version/"

"$script_root/setup_android_sdk.sh"

unset ANDROID_SDK_ROOT

ANDROID_HOME=$custom_android_home \
  ANDROID_NDK_HOME=$custom_android_ndk_home \
  PATH="$custom_android_home/tools/bin:$custom_android_home/platform-tools:$PATH" \
  "$@"
