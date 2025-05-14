#!/bin/bash

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "❌ Usage: $0 <main|pr>"
    exit 1
fi

RUN_MODE=$1  # Accepts "main" or "pr"

# Define correct paths relative to `./ci/`
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="$repo_root/size_reports"
output_file="$output_dir/sizes.csv"
baseline_file="$repo_root/baseline_sizes.csv"
aar="$repo_root/bazel-bin/capture_aar_local.aar"

mkdir -p "$output_dir"

# If running for PR, allow missing baseline file
if [[ "$RUN_MODE" == "pr" && ! -f "$baseline_file" ]]; then
    echo "⚠️ Baseline file not found: $baseline_file — using default values."
    BASELINE_AAR_KB=0
    BASELINE_STRIPPED_SO_KB=0
    BASELINE_COMPRESSED_SO_KB=0
else
    BASELINE_AAR_KB=$(awk -F',' '/AAR/ {print $2}' "$baseline_file" 2>/dev/null || echo "0")
    BASELINE_STRIPPED_SO_KB=$(awk -F',' '/Stripped SO/ {print $2}' "$baseline_file" 2>/dev/null || echo "0")
    BASELINE_COMPRESSED_SO_KB=$(awk -F',' '/Compressed SO/ {print $2}' "$baseline_file" 2>/dev/null || echo "0")
fi

# Ensure AAR file exists
if [ ! -f "$aar" ]; then
    echo "❌ AAR file not found: $aar"
    exit 1
fi

echo "🔍 Running size extraction for: $RUN_MODE"

# Extract and compare sizes
mkdir -p /tmp/ci_compare
pushd /tmp/ci_compare
    unzip "$aar"
    llvm-strip jni/arm64-v8a/libcapture.so
    zip -r stripped_so.zip jni/arm64-v8a/libcapture.so

    # Use Linux-compatible stat command
    CURRENT_AAR_KB=$(stat -c%s "$aar" | awk '{print int($1/1024)}')
    CURRENT_STRIPPED_SO_KB=$(stat -c%s jni/arm64-v8a/libcapture.so | awk '{print int($1/1024)}')
    CURRENT_COMPRESSED_SO_KB=$(stat -c%s stripped_so.zip | awk '{print int($1/1024)}')

    DIFF_AAR_KB=$((CURRENT_AAR_KB - BASELINE_AAR_KB))
    DIFF_STRIPPED_SO_KB=$((CURRENT_STRIPPED_SO_KB - BASELINE_STRIPPED_SO_KB))
    DIFF_COMPRESSED_SO_KB=$((CURRENT_COMPRESSED_SO_KB - BASELINE_COMPRESSED_SO_KB))

    {
        echo "AAR,$BASELINE_AAR_KB,$CURRENT_AAR_KB,$DIFF_AAR_KB";
        echo "Stripped SO,$BASELINE_STRIPPED_SO_KB,$CURRENT_STRIPPED_SO_KB,$DIFF_STRIPPED_SO_KB";
        echo "Compressed SO,$BASELINE_COMPRESSED_SO_KB,$CURRENT_COMPRESSED_SO_KB,$DIFF_COMPRESSED_SO_KB";
    } >> "$output_file"

    {
        echo "AAR_SIZE_MAIN=$BASELINE_AAR_KB";
        echo "AAR_SIZE_PR=$CURRENT_AAR_KB";
        echo "AAR_SIZE_DIFF=$DIFF_AAR_KB";
        echo "STRIPPED_SO_MAIN=$BASELINE_STRIPPED_SO_KB";
        echo "STRIPPED_SO_PR=$CURRENT_STRIPPED_SO_KB";
        echo "STRIPPED_SO_DIFF=$DIFF_STRIPPED_SO_KB";
        echo "COMPRESSED_SO_MAIN=$BASELINE_COMPRESSED_SO_KB";
        echo "COMPRESSED_SO_PR=$CURRENT_COMPRESSED_SO_KB";
        echo "COMPRESSED_SO_DIFF=$DIFF_COMPRESSED_SO_KB";
    } >> "$GITHUB_ENV"

popd
rm -rf /tmp/ci_compare

# Print summary to console
echo "📦 CI Size Comparison Report"
echo "-------------------------------------------"
echo "AAR: Main = ${BASELINE_AAR_KB} KB, PR = ${CURRENT_AAR_KB} KB, Diff = ${DIFF_AAR_KB} KB"
echo "Stripped SO: Main = ${BASELINE_STRIPPED_SO_KB} KB, PR = ${CURRENT_STRIPPED_SO_KB} KB, Diff = ${DIFF_STRIPPED_SO_KB} KB"
echo "Compressed SO: Main = ${BASELINE_COMPRESSED_SO_KB} KB, PR = ${CURRENT_COMPRESSED_SO_KB} KB, Diff = ${DIFF_COMPRESSED_SO_KB} KB"
echo "-------------------------------------------"

# Save baseline CSV (only when running for `main`)
if [[ "$RUN_MODE" == "main" ]]; then
    cp "$output_file" "$baseline_file"
fi
