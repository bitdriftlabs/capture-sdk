#!/bin/bash

set -euo pipefail

repo="$(pwd)"
readonly sdk_repo="$repo"
readonly remote_location_root_prefix="s3://bitdrift-public-dl/sdk/android-maven/io/bitdrift"

readonly version="$1"
readonly capture_archive="$2"
readonly capture_timber_archive="$3"
readonly capture_apollo_archive="$4"
readonly capture_plugin_archive="$5"
readonly capture_plugin_marker_archive="$6"

#############################################
# Helpers for Maven Central bundle creation #
#############################################

# Import a GPG private key if provided via env vars.
# Supports either raw ASCII-armored key in GPG_PRIVATE_KEY or base64-encoded in GPG_PRIVATE_KEY_BASE64.
function import_gpg_key_if_available() {
  if [[ -z "${GPG_PRIVATE_KEY:-${GPG_PRIVATE_KEY_BASE64:-}}" ]]; then
    echo "GPG signing key not provided; cannot proceed with Maven Central bundle creation." >&2
    exit 1
  fi

  # Ensure gpg is available
  if ! command -v gpg >/dev/null 2>&1; then
    echo "gpg is required to sign artifacts; please install it in the CI runner." >&2
    exit 1
  fi

  # Create a temp GNUPGHOME to avoid polluting the runner account
  GNUPGHOME="$(mktemp -d)"
  export GNUPGHOME
  chmod 700 "$GNUPGHOME"

  # Decode if necessary and import (ensure no command echo)
  local _xtrace_was_on=0
  case $- in
    *x*) _xtrace_was_on=1; set +x ;;
  esac

  # Prepare temp files
  local -r key_raw_file="$(mktemp)"
  local -r key_sanitized_file="$(mktemp)"

  if [[ -n "${GPG_PRIVATE_KEY_BASE64:-}" ]]; then
    printf '%s' "$GPG_PRIVATE_KEY_BASE64" | base64 -d >"$key_raw_file"
  else
    printf '%s' "$GPG_PRIVATE_KEY" >"$key_raw_file"
  fi

  # Normalize line endings and extract only the private key block(s)
  tr -d '\r' <"$key_raw_file" | \
    sed -n '/-----BEGIN PGP .*PRIVATE KEY BLOCK-----/,/-----END PGP .*PRIVATE KEY BLOCK-----/p' >"$key_sanitized_file"

  # If sanitize produced nothing, fall back to raw file
  local import_file="$key_sanitized_file"
  if [[ ! -s "$import_file" ]]; then
    import_file="$key_raw_file"
  fi

  # Import, but don't fail the whole script if gpg returns non-zero due to warnings
  set +e
  gpg --batch --yes --import "$import_file"
  local gpg_rc=$?
  set -e
  # If gpg returned a non-zero code, log and continue; we'll verify presence of a secret key below.
  if (( gpg_rc != 0 )); then
    echo "gpg import exited with code $gpg_rc; continuing and verifying secret key presence." >&2
  fi

  # Verify a secret key is available after import
  if ! gpg --batch --list-secret-keys >/dev/null 2>&1; then
    echo "Failed to import GPG private key for signing." >&2
    rm -f "$key_raw_file" "$key_sanitized_file"
    exit 1
  fi

  # Cleanup
  rm -f "$key_raw_file" "$key_sanitized_file"
  if [[ $_xtrace_was_on -eq 1 ]]; then set -x; fi

  # Do not print any key details to logs to avoid leaking identifiers
}

function sign_file() {
  local -r file="$1"
  # --armor --detach-sign to create .asc
  local _xtrace_was_on=0
  case $- in *x*) _xtrace_was_on=1; set +x ;; esac
  if [[ -n "${GPG_PASSPHRASE:-}" ]]; then
    if [[ -n "${GPG_KEY_ID:-}" ]]; then
      printf '%s' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 -u "$GPG_KEY_ID" --armor --detach-sign "$file"
    else
      printf '%s' "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 --armor --detach-sign "$file"
    fi
  else
    if [[ -n "${GPG_KEY_ID:-}" ]]; then
      gpg --batch --yes --pinentry-mode loopback -u "$GPG_KEY_ID" --armor --detach-sign "$file"
    else
      gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file"
    fi
  fi
  if [[ $_xtrace_was_on -eq 1 ]]; then set -x; fi
}

function generate_all_checksums() {
  local -r file="$1"
  "$sdk_repo/ci/checksum.sh" md5 "$file"
  "$sdk_repo/ci/checksum.sh" sha1 "$file"
  "$sdk_repo/ci/checksum.sh" sha256 "$file"
  "$sdk_repo/ci/checksum.sh" sha512 "$file"
}

# Create Maven Central-ready structure under dist/maven-central for a single artifact
# Args:
#   $1 - group path (e.g., io/bitdrift)
#   $2 - artifact id (e.g., capture)
#   $3 - version
#   $4.. - files to include (must be located in current CWD)
function package_maven_central_bundle() {
  local -r group_path="$1"
  local -r artifact_id="$2"
  local -r ver="$3"
  shift 3
  local -a files=("$@")

  local -r out_root="$sdk_repo/dist/maven-central/$group_path/$artifact_id/$ver"
  mkdir -p "$out_root"

  # Copy, sign, and checksum primary files
  for f in "${files[@]}"; do
    if [[ ! -f "$f" ]]; then
      echo "Warning: expected file '$f' not found; skipping." >&2
      continue
    fi
    # Exclude LICENSE/NOTICE from Maven Central bundles (even if accidentally provided)
    case "$(basename "$f" | tr '[:upper:]' '[:lower:]')" in
      license|license.*|notice|notice.*)
        echo "Excluding $(basename "$f") from Maven Central bundle"
        continue
        ;;
    esac
    cp -f "$f" "$out_root/"
    pushd "$out_root" >/dev/null
    sign_file "$(basename "$f")"
    generate_all_checksums "$(basename "$f")"
    popd >/dev/null
  done

  # Zip the group root to ease upload via Sonatype UI/API
  local -r zip_out_dir="$sdk_repo/dist/maven-central"
  mkdir -p "$zip_out_dir"
  local -r zip_name="${artifact_id}-${ver}.maven-central.zip"
  pushd "$zip_out_dir" >/dev/null
  # Create a zip that contains the repo path starting from the group root
  zip -r "$zip_name" "$group_path/$artifact_id/$ver" >/dev/null
  popd >/dev/null
  echo "Created Maven Central bundle: $zip_out_dir/$zip_name"
}

function upload_file() {
  local -r location="$1"
  local -r file="$2"

  "$sdk_repo/ci/checksum.sh" md5 "$file"
  "$sdk_repo/ci/checksum.sh" sha1 "$file"
  "$sdk_repo/ci/checksum.sh" sha256 "$file"
  "$sdk_repo/ci/checksum.sh" sha512 "$file"

  for f in "$file" "$file.md5" "$file.sha1" "$file.sha256" "$file.sha512"; do
    local base
    base="$(basename "$f")"
    echo "Uploading $base to $location/"
    aws s3 cp "$f" "$location/$base" --region us-east-1
  done
}

function generate_maven_file() {
  local -r location="$1"
  local -r library_name="$2"

  echo "+++ Generating maven-metadata.xml for '$location'"

  releases=$(aws s3 ls "$location/" |
    grep -v 'maven-metadata.xml' |
    grep -v 'io.bitdrift' |
    awk '{print $2}' |
    sed 's/^\///;s/\/$//')

  python3 "$sdk_repo/ci/generate_maven_metadata.py" --releases "${releases//$'\n'/,}" --library "$library_name"

  echo "+++ Generated maven-metadata.xml:"
  cat maven-metadata.xml

  upload_file "$location" "maven-metadata.xml"
}

function release_capture_sdk() {
  echo "+++ dl.bitdrift.io Android Capture SDK artifacts upload"

  # We get a zip containing:
  #  * the artifacts named per Maven conventions
  #  * .tar symbols file containing symbols for the release build (e.g., for the .aar).

  pushd "$(mktemp -d)"
  unzip -o "$sdk_repo/$capture_archive"

  echo "+++ Uploading artifacts to s3 bucket"

  local -r remote_location_prefix="$remote_location_root_prefix/capture"
  local -r name="capture-$version"

  files=(
    "$sdk_repo/ci/LICENSE"
    "$name.pom"
    "$name-javadoc.jar"
    "$name-sources.jar"
    "$name-symbols.tar"
    "$name.aar"
  )

  for file in "${files[@]}"; do
    upload_file "$remote_location_prefix/$version" "$file"
  done

  generate_maven_file "$remote_location_prefix" "capture"

  # Prepare Maven Central bundle (group: io/bitdrift, artifact: capture)
  package_maven_central_bundle "io/bitdrift" "capture" "$version" \
    "$name.pom" "$name-javadoc.jar" "$name-sources.jar" "$name.aar"
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
  aws s3 cp "$sdk_repo/ci/LICENSE" "$remote_location_prefix/$version/LICENSE" --region us-east-1

  # Upload all the files in the zip
  aws s3 cp . "$remote_location_prefix/$version/" --recursive --region us-east-1

  generate_maven_file "$remote_location_prefix" "$library_name"

  # Prepare Maven Central bundle (group: io/bitdrift, artifact: $library_name)
  # Try to derive base from .pom name
  shopt -s nullglob
  local poms=( *.pom )
  if (( ${#poms[@]} > 0 )); then
    local base="${poms[0]%.pom}"
    # Select common files if present
    local -a bundle_files=( "$base.pom" )
    [[ -f "$base.jar" ]] && bundle_files+=( "$base.jar" )
    [[ -f "$base.aar" ]] && bundle_files+=( "$base.aar" )
    [[ -f "$base-sources.jar" ]] && bundle_files+=( "$base-sources.jar" )
    [[ -f "$base-javadoc.jar" ]] && bundle_files+=( "$base-javadoc.jar" )
    # Explicitly exclude LICENSE/NOTICE from bundle files if present nearby
    for i in "${!bundle_files[@]}"; do
      case "$(basename "${bundle_files[$i]}" | tr '[:upper:]' '[:lower:]')" in
        license|license.*|notice|notice.*)
          unset 'bundle_files[$i]'
          ;;
      esac
    done
    package_maven_central_bundle "io/bitdrift" "$library_name" "$version" "${bundle_files[@]}"
  else
    echo "Warning: No .pom found for $library_name; skipping Maven Central bundle."
  fi
  shopt -u nullglob
  popd
}

function release_gradle_plugin() {
  local -r plugin_name="$1"
  local -r plugin_marker="$2"
  local -r archive="$3"

  echo "+++ dl.bitdrift.io Android Integration plugin $plugin_name / $plugin_marker artifacts upload"

  local -r remote_location_prefix="$remote_location_root_prefix/$plugin_name/$plugin_marker"

  pushd "$(mktemp -d)"
  unzip -o "$sdk_repo/$archive"

  aws s3 cp "$sdk_repo/ci/LICENSE" "$remote_location_prefix/$version/LICENSE" --region us-east-1

  aws s3 cp . "$remote_location_prefix/$version/" --recursive --region us-east-1

  generate_maven_file "$remote_location_prefix" "$plugin_marker"

  # Prepare Maven Central bundle (group: io/bitdrift/capture-plugin, artifact: io.bitdrift.capture-plugin.gradle.plugin)
  shopt -s nullglob
  local poms=( *.pom )
  if (( ${#poms[@]} > 0 )); then
    local base="${poms[0]%.pom}"
    package_maven_central_bundle "io/bitdrift/$plugin_name" "$plugin_marker" "$version" "$base.pom"
  else
    echo "Warning: No .pom found for $plugin_marker; skipping Maven Central bundle."
  fi
  shopt -u nullglob

  popd
}

# If requested, set up GPG for signing
import_gpg_key_if_available

release_capture_sdk
release_gradle_library "capture-timber" "$capture_timber_archive"
release_gradle_library "capture-apollo" "$capture_apollo_archive"
release_gradle_library "capture-plugin" "$capture_plugin_archive"
release_gradle_plugin "capture-plugin" "io.bitdrift.capture-plugin.gradle.plugin" "$capture_plugin_marker_archive"
