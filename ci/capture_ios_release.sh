#!/bin/bash

set -euo pipefail

echo "+++ dl.bitdrift.io iOS artifacts upload"

readonly version="$1"

sdk_repo="$(pwd)"
readonly sdk_repo="$sdk_repo"

function upload_file() {
  local -r library="$1"
  local -r file="$2"

  "$sdk_repo/ci/checksum.sh" sha256 "$file"

  for f in "$file" "$file.sha256"; do
    echo "+ Uploading $f..."
    aws s3 cp "$f" "s3://bitdrift-public-dl/sdk/ios/$library-$version/$f" --region us-east-1
  done
}

function prepare_and_upload_library_artifacts() {
  local -r library="$1"
  # Change camelCase to snake_case with `_` replaced by "-".
  local -r normalized_library=$(echo "$library" | sed 's/\([A-Z]\)/-\L\1/g;s/^-//')

  echo "+++ Preparing $library artifacts"

  # For legacy reasons we publish these artifacts without the `.ios` suffix.
  mv "$library.doccarchive.ios.zip" "$library.doccarchive.zip"
  mv "$library.ios.zip" "$library.zip"

  files=(\
    "$library.doccarchive.zip" \
    "$library.zip" \
  )

  echo "+++ Uploading $library artifacts to s3 bucket"

  for file in "${files[@]}"; do
    upload_file "$normalized_library" "$file"
  done
}

prepare_and_upload_library_artifacts "Capture"
