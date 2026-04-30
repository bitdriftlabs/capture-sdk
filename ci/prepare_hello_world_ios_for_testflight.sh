#!/bin/bash

set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <marketing-version> <build-number>" >&2
  echo "example: $0 0.1.0 7" >&2
  exit 1
fi

readonly marketing_version="$1"
readonly build_number="$2"
readonly target="//examples/swift/hello_world:hello_world_app"
readonly embed_label="HelloWorld_${marketing_version}_build_${build_number}"

echo "Building release IPA for $target"
echo "Version: $marketing_version ($build_number)"

./bazelw build \
  --config=release-ios \
  --cpu=ios_arm64 \
  --apple_generate_dsym \
  --output_groups=+dsyms \
  --embed_label="$embed_label" \
  "$target"

readonly dsym_output_dir="bazel-bin/examples/swift/hello_world/hello_world_app_dsyms"

dsym_paths=()
while IFS= read -r dsym_path; do
  dsym_paths+=("$dsym_path")
done < <(find -L "$dsym_output_dir" -maxdepth 1 -name "*.dSYM" -print | sort)

if [ "${#dsym_paths[@]}" -eq 0 ]; then
  echo "error: no dSYM outputs found under $dsym_output_dir" >&2
  exit 1
fi

echo
echo "IPA ready at:"
echo "  bazel-bin/examples/swift/hello_world/hello_world_app.ipa"
echo "dSYM outputs:"
printf '  %s\n' "${dsym_paths[@]}"
echo
