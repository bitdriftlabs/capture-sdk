#!/bin/bash

set -euo pipefail

# Default to arm64-v8a if no architecture specified
ARCH=${1:-arm64-v8a}

# Build the .aar with Android release flags
./bazelw build :capture_aar --config release-android --android_platforms=@rules_android//:"$ARCH"

aar=$(pwd)/bazel-bin/capture_aar_local.aar

mkdir -p /tmp/bitdrift_so
pushd /tmp/bitdrift_so
    # Unzip the .aar, strip the .so and then re-zip it.
	unzip "$aar"
	
	# Determine JNI path based on architecture
	if [[ "$ARCH" == "x86_64" ]]; then
	    JNI_PATH="jni/x86_64"
	else
	    JNI_PATH="jni/arm64-v8a"
	fi
	
	llvm-strip "$JNI_PATH/libcapture.so"
	zip -r aar.zip "$JNI_PATH/libcapture.so"
	# print size in kiB for higher granularity.
	du -k aar.zip
	du -h aar.zip
	
	# Output just the size in KB for CI parsing
	size_kb=$(du -k aar.zip | awk '{print $1}')
	echo "SO_SIZE_KB=$size_kb"
popd

# Cleanup
rm -rf /tmp/bitdrift_so
