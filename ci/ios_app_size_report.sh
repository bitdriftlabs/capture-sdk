#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Builds an iOS archive for the hello world sample app, exports a thinned IPA, and
prints the compressed app size reported by Xcode's App Thinning report.

Environment variables:
  IOS_APP_SIZE_DEVICE_MODEL
      Optional device model identifier used for thinning/export. When omitted,
      the report parser accepts a single shared variant block. Default: unset
  IOS_APP_SIZE_OUTPUT_DIR
      Directory for intermediate archive/export files. Default: ios_app_size_output
  IOS_APP_SIZE_EXPORT_METHOD
      xcodebuild export method. Default: release-testing (aka. adhoc)

Output:
  IOS_APP_SIZE_KB=<rounded compressed app size in KB>
  IOS_APP_SIZE_REPORT=<path to App Thinning Size Report.txt>
  IOS_APP_SIZE_EXPORT_DIR=<path to exported IPA directory>
EOF
}

if [[ "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

DEVICE_MODEL="${IOS_APP_SIZE_DEVICE_MODEL:-}"
OUTPUT_DIR="${IOS_APP_SIZE_OUTPUT_DIR:-ios_app_size_output}"
ARCHIVE_TARGET="//examples/swift/hello_world:ios_app_archive"
ARCHIVE_PATH="bazel-bin/examples/swift/hello_world/Bitdrift Sample App.xcarchive"
EXPORT_ARCHIVE_PATH="$OUTPUT_DIR/Bitdrift Sample App.xcarchive"
EXPORT_OPTIONS_PLIST="$OUTPUT_DIR/ExportOptions.plist"
EXPORT_DIR="$OUTPUT_DIR/export"
EXPORT_METHOD="${IOS_APP_SIZE_EXPORT_METHOD:-release-testing}"
PROFILE_PLIST="$OUTPUT_DIR/embedded_profile.plist"
ENTITLEMENTS_PLIST="$OUTPUT_DIR/export_entitlements.plist"

plist_get() {
  local plist_path="$1"
  local key_path="$2"

  /usr/libexec/PlistBuddy -c "Print $key_path" "$plist_path"
}

prepare_output_dir() {
  if [[ -d "$OUTPUT_DIR" ]]; then
    chmod -R u+w "$OUTPUT_DIR"
  fi
  rm -rf "$OUTPUT_DIR"
  mkdir -p "$EXPORT_DIR"
}

build_archive() {
  ./bazelw build \
    --config ci \
    --config release-ios \
    --cpu=ios_arm64 \
    "$ARCHIVE_TARGET"

  if [[ ! -d "$ARCHIVE_PATH" ]]; then
    echo "Expected xcarchive not found at $ARCHIVE_PATH" >&2
    exit 1
  fi
}

extract_profile_plist() {
  local embedded_profile="$1"

  if ! security cms -D -i "$embedded_profile" > "$PROFILE_PLIST" 2> /dev/null; then
    python3 ci/extract_embedded_profile_plist.py "$embedded_profile" "$PROFILE_PLIST"
  fi
}

strip_data_protection_entitlement() {
  local archive_path="$1"
  local application_path
  local app_path
  local embedded_profile
  local signing_identity

  # The archive already contains a signed .app and embedded provisioning profile.
  # We decode that profile, remove the default data protection entitlement that
  # blocks the export flow, and re-sign the copied app before xcodebuild export.
  application_path="$(plist_get "$archive_path/Info.plist" ":ApplicationProperties:ApplicationPath")"
  app_path="$archive_path/Products/$application_path"
  embedded_profile="$app_path/embedded.mobileprovision"
  signing_identity="$(plist_get "$archive_path/Info.plist" ":ApplicationProperties:SigningIdentity")"

  if [[ ! -f "$embedded_profile" ]]; then
    echo "Expected embedded provisioning profile not found at $embedded_profile" >&2
    exit 1
  fi

  extract_profile_plist "$embedded_profile"

  plutil -extract Entitlements xml1 -o "$ENTITLEMENTS_PLIST" "$PROFILE_PLIST"

  if plist_get "$ENTITLEMENTS_PLIST" ":com.apple.developer.default-data-protection" > /dev/null 2>&1; then
    /usr/libexec/PlistBuddy -c "Delete :com.apple.developer.default-data-protection" "$ENTITLEMENTS_PLIST"
  fi

  codesign --force --sign "$signing_identity" --entitlements "$ENTITLEMENTS_PLIST" "$app_path"
}

copy_archive_for_export() {
  # Export operates on a writable copy so we can adjust signing inputs without
  # mutating Bazel's archived output.
  ditto "$ARCHIVE_PATH" "$EXPORT_ARCHIVE_PATH"
  chmod -R u+w "$EXPORT_ARCHIVE_PATH"
}

write_export_options() {
  local app_bundle_id="$1"
  local team_id="$2"
  local signing_identity="$3"
  local profile_name="$4"

  /usr/libexec/PlistBuddy -c "Add :destination string export" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :method string $EXPORT_METHOD" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :signingStyle string manual" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :teamID string $team_id" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :signingCertificate string $signing_identity" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :provisioningProfiles dict" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :provisioningProfiles:$app_bundle_id string $profile_name" "$EXPORT_OPTIONS_PLIST"
  /usr/libexec/PlistBuddy -c "Add :stripSwiftSymbols bool true" "$EXPORT_OPTIONS_PLIST"

  if [[ -n "$DEVICE_MODEL" ]]; then
    /usr/libexec/PlistBuddy -c "Add :thinning string $DEVICE_MODEL" "$EXPORT_OPTIONS_PLIST"
  else
    /usr/libexec/PlistBuddy -c "Add :thinning string <thin-for-all-variants>" "$EXPORT_OPTIONS_PLIST"
  fi
}

export_archive() {
  xcodebuild -exportArchive \
    -archivePath "$EXPORT_ARCHIVE_PATH" \
    -exportPath "$EXPORT_DIR" \
    -exportOptionsPlist "$EXPORT_OPTIONS_PLIST"
}

prepare_output_dir
build_archive
copy_archive_for_export
strip_data_protection_entitlement "$EXPORT_ARCHIVE_PATH"

APP_BUNDLE_ID="$(plist_get "$EXPORT_ARCHIVE_PATH/Info.plist" ":ApplicationProperties:CFBundleIdentifier")"
TEAM_ID="$(plist_get "$EXPORT_ARCHIVE_PATH/Info.plist" ":ApplicationProperties:Team")"
SIGNING_IDENTITY="$(plist_get "$EXPORT_ARCHIVE_PATH/Info.plist" ":ApplicationProperties:SigningIdentity")"
PROFILE_NAME="$(plist_get "$PROFILE_PLIST" ":Name")"

write_export_options "$APP_BUNDLE_ID" "$TEAM_ID" "$SIGNING_IDENTITY" "$PROFILE_NAME"
export_archive

REPORT_PATH="$EXPORT_DIR/App Thinning Size Report.txt"
if [[ ! -f "$REPORT_PATH" ]]; then
  echo "Expected App Thinning Size Report not found at $REPORT_PATH" >&2
  exit 1
fi

if [[ -n "$DEVICE_MODEL" ]]; then
  IOS_APP_SIZE_KB="$(python3 ci/parse_ios_app_thinning_report.py "$REPORT_PATH" "$DEVICE_MODEL")"
else
  IOS_APP_SIZE_KB="$(python3 ci/parse_ios_app_thinning_report.py "$REPORT_PATH")"
fi
echo "IOS_APP_SIZE_KB=$IOS_APP_SIZE_KB"
echo "IOS_APP_SIZE_REPORT=$REPORT_PATH"
echo "IOS_APP_SIZE_EXPORT_DIR=$EXPORT_DIR"
