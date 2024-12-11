#!/bin/bash

set -euo pipefail

repo="$(pwd)"
readonly sdk_repo="$repo"
readonly remote_location_root_prefix="s3://bitdrift-public-dl/sdk/android-maven/io/bitdrift"

readonly version="$1"
readonly capture_archive="$2"
readonly capture_timber_archive="$3"
readonly capture_apollo3_archive="$4"
readonly capture_plugin_archive="$5"

function upload_file() {
  local -r location="$1"
  local -r file="$2"

  "$sdk_repo/ci/checksum.sh" md5 "$file"
  "$sdk_repo/ci/checksum.sh" sha1 "$file"
  "$sdk_repo/ci/checksum.sh" sha256 "$file"
  "$sdk_repo/ci/checksum.sh" sha512 "$file"

  for f in "$file" "$file.md5" "$file.sha1" "$file.sha256" "$file.sha512"; do
    echo "Uploading $file..."
    aws s3 cp "$f" "$location/$f" --region us-east-1
  done
}

function generate_maven_file() {
  local -r location="$1"
  local -r library_name="$2"

  echo "+++ Generating maven-metadata.xml for '$location'"

  releases=$(aws s3 ls "$location/" \
    | grep -v 'maven-metadata.xml' \
    | awk '{print $2}' \
    | sed 's/^\///;s/\/$//')

  python3 "$sdk_repo/ci/generate_maven_metadata.py" --releases "${releases//$'\n'/,}" --library-name "$library_name"

  echo "+++ Generated maven-metadata.xml:"
  cat maven-metadata.xml

  upload_file "$remote_location_prefix" "maven-metadata.xml"
}

function release_capture_sdk() {
  echo "+++ dl.bitdrift.io Android Capture SDK artifacts upload"

  # We get a zip containing:
  #  * the artifacts named per Maven conventions
  #  * .tar symbols file containing symbols for the stripped release shared libraries.
  #  * shared dylib libraries

  pushd "$(mktemp -d)"
    unzip -o "$sdk_repo/$capture_archive"

    echo "+++ Uploading artifacts to s3 bucket"

    local -r remote_location_prefix="$remote_location_root_prefix/capture"
    local -r name="capture-$version"

    files=(\
      "$sdk_repo/ci/LICENSE.txt" \
      "$sdk_repo/ci/NOTICE.txt" \
      "$name.pom" \
      "$name-javadoc.jar" \
      "$name-sources.jar" \
      "$name-symbols.tar" \
      "$name-dylib.tar" \
      "$name.aar" \
    )

    for file in "${files[@]}"; do
      upload_file "$remote_location_prefix/$version" "$file"
    done

    generate_maven_file "$remote_location_prefix" "capture"
  popd
}

function release_gradle_library() {
  local -r library_name="$1"
  local -r archive="$2"

  echo "+++ dl.bitdrift.io Android Integration $library_name artifacts upload"

  local -r remote_location_prefix="$remote_location_root_prefix/$library_name"

  pushd "$(mktemp -d)"
    unzip -o "$sdk_repo/$archive"
    
    # Update the per-version files
    aws s3 cp "$sdk_repo/ci/LICENSE.txt" "$remote_location_prefix/$version/LICENSE.txt" --region us-east-1
    aws s3 cp "$sdk_repo/ci/NOTICE.txt" "$remote_location_prefix/$version/NOTICE.txt" --region us-east-1

    aws s3 cp "*" "$remote_location_prefix/$version/" --recursive --region us-east-1

    generate_maven_file "$remote_location_prefix" "$library_name"
  popd
}

release_capture_sdk
release_gradle_library "capture-timber" "$capture_timber_archive"
release_gradle_library "capture-apollo3" "$capture_apollo3_archive"
release_gradle_library "capture-plugin" "$capture_plugin_archive"
