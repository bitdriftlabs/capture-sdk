#!/bin/bash

set -euo pipefail

echo "+++ Building Android Hello World Example App"

pushd "platform/jvm"
./gradlew gradle-test-app:assembleDebug --info
popd

mkdir -p dist

sdk_repo="$(pwd)"
output="$(mktemp -d)"

pushd "$(mktemp -d)"
  mv "$sdk_repo/platform/jvm/gradle-test-app/build/outputs/apk/debug/gradle-test-app-debug.apk" "$output/gradle-test-app-debug.apk"
popd

echo "+++ Bundling Android Example apps"

pushd "$output"
  zip -r android_example_apps.zip gradle-test-app-debug.apk
popd

rm -rf dist/android_example_apps.zip
mv "$output"/android_example_apps.zip dist/example-apps.android.zip
