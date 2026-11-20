#!/usr/bin/env bash

set -euo pipefail

artifacts=false
timber=false
plugin=false
licenses=false
instrumentation=false

if [[ "${GITHUB_EVENT_NAME:-}" == push ]]; then
  artifacts=true
  timber=true
  plugin=true
  licenses=true
  instrumentation=true
else
  base="${1:?Expected the PR base SHA}"
  head="${2:?Expected the PR head SHA}"
  changed_paths="$(mktemp)"
  trap 'rm -f "$changed_paths"' EXIT
  git diff --name-only -z --no-renames "$base...$head" -- > "$changed_paths"

  while IFS= read -r -d '' path; do
    case "$path" in
      *.md | docs/*) continue ;;
    esac
    artifacts=true

    case "$path" in
      .github/workflows/android.yaml | .github/actions/setup/* | .github/actions/android-setup/* | \
        .github/actions/setup-android-emulator/* | ci/android_ci_changes.sh | \
        tools/android_sdk_wrapper.sh | tools/setup_android_sdk.sh | tools/*android_toolchain_versions.sh | \
        platform/jvm/buildSrc/* | platform/jvm/gradle/* | platform/jvm/settings.gradle.kts | \
        platform/jvm/gradle.properties | platform/jvm/gradlew | platform/jvm/gradlew.bat)
        timber=true
        plugin=true
        licenses=true
        instrumentation=true
        ;;
      platform/jvm/capture-plugin/*)
        plugin=true
        ;;
      platform/jvm/capture-timber/src/test/*)
        timber=true
        ;;
      platform/jvm/capture-timber/* | platform/jvm/capture/src/main/* | \
        platform/jvm/common/src/main/* | platform/jvm/replay/src/main/*)
        timber=true
        instrumentation=true
        ;;
      platform/jvm/capture/build.gradle.kts | platform/jvm/common/build.gradle.kts | platform/jvm/replay/build.gradle.kts)
        timber=true
        licenses=true
        instrumentation=true
        ;;
      platform/jvm/allowed-licenses.json)
        licenses=true
        ;;
      # Keep the Gradle packaging and JNI load path covered. SDK implementation
      # changes in Rust are exercised by Bazel and the Hello World APK instead.
      platform/jvm/gradle-test-app/* | platform/jvm/capture-apollo/* | \
        platform/jvm/capture/*.pro | platform/jvm/common/*.pro | platform/jvm/replay/*.pro | \
        platform/jvm/AndroidManifest.xml | platform/jvm/Cargo.toml | platform/jvm/jni_symbols.lds | \
        platform/jvm/core/BUILD.bazel | platform/jvm/core/Cargo.toml | \
        platform/jvm/core/src/jni.rs | platform/jvm/core/src/lib.rs | .cargo/* | rust-toolchain*)
        instrumentation=true
        ;;
    esac
  done < "$changed_paths"
fi

printf '%s\n' \
  "artifacts=$artifacts" "timber=$timber" "plugin=$plugin" \
  "licenses=$licenses" "instrumentation=$instrumentation" >> "${GITHUB_OUTPUT:?Expected GITHUB_OUTPUT}"
