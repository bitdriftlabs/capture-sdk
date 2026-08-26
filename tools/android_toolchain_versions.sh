#!/bin/bash

# shellcheck disable=SC2034

# Versions shared by the local Android SDK cache and Bazel. Bazel alone owns the NDK
# configuration; keep these values in sync with MODULE.bazel through
# tools/check_android_toolchain_versions.sh.

readonly android_sdk_api_level="36"
readonly android_build_tools_version="36.1.0"
readonly android_ndk_alias="r27c"
readonly android_ndk_api_level="21"
