#!/bin/bash

# shellcheck disable=SC2034

# Versions used by the local Android SDK cache for Gradle and emulator workflows.
# MODULE.bazel carries the corresponding Bazel configuration; keep the two in sync with
# tools/check_android_toolchain_versions.sh.

readonly android_sdk_api_level="36"
readonly android_build_tools_version="36.1.0"
readonly android_ndk_version="27.2.12479018"
readonly android_ndk_alias="r27c"
readonly android_ndk_api_level="21"
