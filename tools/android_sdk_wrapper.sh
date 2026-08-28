#!/bin/bash

set -euo pipefail

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_root
# shellcheck source=tools/android_toolchain_versions.sh
source "$script_root/android_toolchain_versions.sh"
readonly softlink_root_dir="/tmp/bitdrift-android-sdk"
readonly custom_android_home="$softlink_root_dir/android-sdk-unarchived"

"$script_root/setup_android_sdk.sh"

unset ANDROID_SDK_ROOT

ANDROID_HOME=$custom_android_home \
  PATH="$custom_android_home/cmdline-tools/$android_cmdline_tools_version/bin:$custom_android_home/platform-tools:$PATH" \
  "$@"
