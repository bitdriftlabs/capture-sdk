#!/bin/bash

set -euo pipefail

# Default to arm64-v8a if no architecture is specified. Passing --aar reuses
# an existing AAR instead of building one, which is useful when CI has already
# built it as part of another target.
ARCH="arm64-v8a"
AAR=""

usage() {
    cat <<'EOF'
Usage: tools/capture_so_size.sh [--aar PATH] [ARCH]

Build the capture AAR for ARCH and print its stripped native-library size.

Options:
  --aar PATH  Reuse an existing AAR instead of invoking Bazel.
  -h, --help  Show this help text.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --aar)
            if [[ $# -lt 2 ]]; then
                echo "error: --aar requires a path" >&2
                exit 2
            fi
            AAR="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            echo "error: unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
        *)
            ARCH="$1"
            shift
            ;;
    esac
done

if [[ -z "$AAR" ]]; then
    # Build the .aar with Android release flags for local use.
    ./bazelw build :capture_aar --config release-android --android_platforms=@rules_android//:"$ARCH"
    AAR="$PWD/bazel-bin/capture_aar_local.aar"
elif [[ "$AAR" != /* ]]; then
    AAR="$PWD/$AAR"
fi

if [[ ! -f "$AAR" ]]; then
    echo "error: AAR not found: $AAR" >&2
    exit 1
fi

work_dir=$(mktemp -d /tmp/bitdrift_so.XXXXXX)
trap 'rm -rf "$work_dir"' EXIT
pushd "$work_dir"

# Unzip the AAR, strip the .so, and re-zip it.
unzip "$AAR"

# Determine JNI path based on architecture.
if [[ "$ARCH" == "x86_64" ]]; then
    JNI_PATH="jni/x86_64"
else
    JNI_PATH="jni/arm64-v8a"
fi

llvm-strip "$JNI_PATH/libcapture.so"
zip -r aar.zip "$JNI_PATH/libcapture.so"
# Print size in KiB for higher granularity.
du -k aar.zip
du -h aar.zip

# Output just the size in KB for CI parsing.
size_kb=$(du -k aar.zip | awk '{print $1}')
echo "SO_SIZE_KB=$size_kb"
