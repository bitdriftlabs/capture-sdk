#!/bin/bash

set -euo pipefail

function create_MD5_file() {
  local -r file="$1"
  echo "+ Generating MD5 checksum for $file"
  "$(md5sum "$file" | awk '{print $1}')" > "$file.md5"
  cat "$file.md5"
}

function create_SHA1_file() {
  local -r file="$1"
  echo "+ Generating SHA-1 checksum for $file"
  "$(sha1sum "$file" | awk '{print $1}')" > "$file.sha1"
  cat "$file.sha1"
}

function create_SHA256_file() {
  local -r file="$1"
  echo "+ Generating SHA256 checksum for $file"
  "$(shasum -a 256 "$file" | awk '{print $1}')" > "$file.sha256"
  cat "$file.sha256"
}

function create_SHA512_file() {
  local -r file="$1"
  echo "+ Generating SHA512 checksum for $file"
  "$(shasum -a 512 "$file" | awk '{print $1}')" > "$file.sha512"
  cat "$file.sha512"
}

# Introduce main function so that we can use local readonly variables inside of it (local -r).
function main() {
  local -r checksum_type="$1"
  local -r file="$2"

  case "$checksum_type" in 
    "md5")
      create_MD5_file "$file"
      ;;
    "sha1")
      create_SHA1_file "$file"
      ;;
    "sha256")
      create_SHA256_file "$file"
      ;;
    "sha512")
      create_SHA512_file "$file"
      ;;
  esac
}

main "$@"
