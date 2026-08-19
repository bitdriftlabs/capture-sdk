#!/bin/bash

set -euo pipefail

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_root
repo_root="$(cd "$script_root/.." && pwd)"
readonly repo_root

# shellcheck source=tools/android_toolchain_versions.sh
source "$script_root/android_toolchain_versions.sh"

fail() {
  echo "Android toolchain version drift: $1" >&2
  exit 1
}

module_sdk_api="$(sed -nE 's/^SDK_API_LEVEL = ([0-9]+)$/\1/p' "$repo_root/MODULE.bazel")"
module_build_tools="$(sed -nE 's/^SDK_BUILD_TOOLS_VERSION = "([^"]+)"$/\1/p' "$repo_root/MODULE.bazel")"
module_ndk_api="$(sed -nE 's/^NDK_API_LEVEL = ([0-9]+)$/\1/p' "$repo_root/MODULE.bazel")"
module_ndk_alias="$(sed -nE '/^android\.ndk\(/,/^\)/ s/^    version = "([^"]+)",$/\1/p' "$repo_root/MODULE.bazel")"

[[ "$module_sdk_api" == "$android_sdk_api_level" ]] || fail "MODULE.bazel SDK API is $module_sdk_api, expected $android_sdk_api_level"
[[ "$module_build_tools" == "$android_build_tools_version" ]] || fail "MODULE.bazel build-tools is $module_build_tools, expected $android_build_tools_version"
[[ "$module_ndk_api" == "$android_ndk_api_level" ]] || fail "MODULE.bazel NDK API is $module_ndk_api, expected $android_ndk_api_level"
[[ "$module_ndk_alias" == "$android_ndk_alias" ]] || fail "MODULE.bazel NDK is $module_ndk_alias, expected $android_ndk_alias"

if ! grep -Fq "platforms;android-\$android_sdk_api_level" "$script_root/setup_android_sdk.sh"; then
  fail "the Gradle SDK setup does not install the configured platform API"
fi
if ! grep -Fq "build-tools;\$android_build_tools_version" "$script_root/setup_android_sdk.sh"; then
  fail "the Gradle SDK setup does not install the configured build-tools"
fi
if ! grep -Fq "ndk;\$ndk_version" "$script_root/setup_android_sdk.sh"; then
  fail "the Gradle SDK setup does not install the configured NDK"
fi

while IFS= read -r gradle_sdk_api; do
  [[ "$gradle_sdk_api" == "$android_sdk_api_level" ]] || fail "a Gradle project compiles against API $gradle_sdk_api, expected $android_sdk_api_level"
done < <(find "$repo_root/platform/jvm" "$repo_root/gradle" -type f \( -name '*.gradle' -o -name '*.gradle.kts' \) -exec grep -hE 'compileSdk[[:space:]]*(=[[:space:]]*)?[0-9]+' {} + | sed -nE 's/.*compileSdk[[:space:]]*(=[[:space:]]*)?([0-9]+).*/\2/p' | sort -u)

while IFS= read -r gradle_ndk_version; do
  [[ "$gradle_ndk_version" == "$android_ndk_version" ]] || fail "a Gradle project uses NDK $gradle_ndk_version, expected $android_ndk_version"
done < <(find "$repo_root/platform/jvm" "$repo_root/gradle" -type f \( -name '*.gradle' -o -name '*.gradle.kts' \) -exec grep -hE 'ndkVersion[[:space:]]*=[[:space:]]*"[0-9.]+"' {} + | sed -nE 's/.*ndkVersion[[:space:]]*=[[:space:]]*"([0-9.]+)".*/\1/p' | sort -u)

echo "Android Bazel and Gradle toolchain versions are aligned."
