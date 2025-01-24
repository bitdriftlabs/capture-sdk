#!/bin/bash

set -euo pipefail

version="$1"

# Replace the `capture_version = '1.0.0'` with the new version number
sed -i "s/capture_version = '.*/capture_version = '$version'/g" packages/react-native/BdReactNative.podspec

# Replace `id 'io.bitdrift.capture-plugin' version '0.16.6'` with the new version number
sed -i "s/id 'io.bitdrift.capture-plugin' version '.*/id 'io.bitdrift.capture-plugin' version '$version'/g" packages/react-native/src/plugin/withAndroid.ts

# Replace `api 'io.bitdrift.capture:0.16.5'` with the new version number
sed -i "s/api 'io\.bitdrift:capture:.*/api 'io.bitdrift:capture:$version'/g" packages/react-native/android/build.gradle


