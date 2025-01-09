#!/bin/bash

set -euo pipefail

# Build the .aar with Android release flags
./bazelw build :capture_aar --config release-android --android_platforms=@rules_android//:arm64-v8a

aar=$(pwd)/bazel-bin/capture_aar_local.aar

mkdir -p /tmp/bitdrift_so
pushd /tmp/bitdrift_so
    # Unzip the .aar, strip the .so and then re-zip it.
	unzip "$aar"
	llvm-strip jni/arm64-v8a/libcapture.so
	zip -r aar.zip jni/arm64-v8a/libcapture.so
	# print size in kiB for higher granularity.
	du -k aar.zip
	du -h aar.zip
popd

# Cleanup
rm -rf /tmp/bitdrift_so
