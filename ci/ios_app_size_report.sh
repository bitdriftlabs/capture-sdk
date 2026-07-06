#!/usr/bin/env bash

set -euo pipefail

DEVICE_MODEL="${IOS_APP_SIZE_DEVICE_MODEL:-iPhone18,3}"
OUTPUT_DIR="${IOS_APP_SIZE_OUTPUT_DIR:-ios_app_size_output}"
ARCHIVE_TARGET="//examples/swift/hello_world:ios_app_archive"
ARCHIVE_PATH="bazel-bin/examples/swift/hello_world/Bitdrift Sample App.xcarchive"
EXPORT_ARCHIVE_PATH="$OUTPUT_DIR/Bitdrift Sample App.xcarchive"
EXPORT_OPTIONS_PLIST="$OUTPUT_DIR/ExportOptions.plist"
EXPORT_DIR="$OUTPUT_DIR/export"

strip_data_protection_entitlement() {
  local archive_path="$1"
  local application_path
  local app_path
  local embedded_profile
  local profile_plist
  local signing_identity
  local entitlements_plist

  application_path="$(/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:ApplicationPath" "$archive_path/Info.plist")"
  app_path="$archive_path/Products/$application_path"
  embedded_profile="$app_path/embedded.mobileprovision"
  profile_plist="$OUTPUT_DIR/embedded_profile.plist"
  signing_identity="$(/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:SigningIdentity" "$archive_path/Info.plist")"
  entitlements_plist="$OUTPUT_DIR/export_entitlements.plist"

  if [[ ! -f "$embedded_profile" ]]; then
    echo "Expected embedded provisioning profile not found at $embedded_profile" >&2
    exit 1
  fi

  if ! security cms -D -i "$embedded_profile" > "$profile_plist" 2> /dev/null; then
    python3 - "$embedded_profile" "$profile_plist" <<'PY'
import sys

source, destination = sys.argv[1:]
data = open(source, "rb").read()
start = data.find(b"<?xml")
end = data.find(b"</plist>")
if start == -1 or end == -1:
    raise SystemExit("could not extract plist payload from embedded provisioning profile")
open(destination, "wb").write(data[start : end + len(b"</plist>")])
PY
  fi

  plutil -extract Entitlements xml1 -o "$entitlements_plist" "$profile_plist"

  if /usr/libexec/PlistBuddy -c "Print :com.apple.developer.default-data-protection" "$entitlements_plist" > /dev/null 2>&1; then
    /usr/libexec/PlistBuddy -c "Delete :com.apple.developer.default-data-protection" "$entitlements_plist"
  fi

  codesign --force --sign "$signing_identity" --entitlements "$entitlements_plist" "$app_path"
}

if [[ -d "$OUTPUT_DIR" ]]; then
  chmod -R u+w "$OUTPUT_DIR"
fi
rm -rf "$OUTPUT_DIR"
mkdir -p "$EXPORT_DIR"

./bazelw build \
  --config ci \
  --config release-ios \
  --cpu=ios_arm64 \
  "$ARCHIVE_TARGET"

if [[ ! -d "$ARCHIVE_PATH" ]]; then
  echo "Expected xcarchive not found at $ARCHIVE_PATH" >&2
  exit 1
fi

ditto "$ARCHIVE_PATH" "$EXPORT_ARCHIVE_PATH"
chmod -R u+w "$EXPORT_ARCHIVE_PATH"
strip_data_protection_entitlement "$EXPORT_ARCHIVE_PATH"

/usr/libexec/PlistBuddy -c "Add :destination string export" "$EXPORT_OPTIONS_PLIST"
/usr/libexec/PlistBuddy -c "Add :method string debugging" "$EXPORT_OPTIONS_PLIST"
/usr/libexec/PlistBuddy -c "Add :stripSwiftSymbols bool true" "$EXPORT_OPTIONS_PLIST"
/usr/libexec/PlistBuddy -c "Add :thinning string $DEVICE_MODEL" "$EXPORT_OPTIONS_PLIST"

xcodebuild -exportArchive \
  -archivePath "$EXPORT_ARCHIVE_PATH" \
  -exportPath "$EXPORT_DIR" \
  -exportOptionsPlist "$EXPORT_OPTIONS_PLIST"

REPORT_PATH="$EXPORT_DIR/App Thinning Size Report.txt"
if [[ ! -f "$REPORT_PATH" ]]; then
  echo "Expected App Thinning Size Report not found at $REPORT_PATH" >&2
  exit 1
fi

IOS_APP_SIZE_KB="$(python3 ci/parse_ios_app_thinning_report.py "$REPORT_PATH" "$DEVICE_MODEL")"
echo "IOS_APP_SIZE_KB=$IOS_APP_SIZE_KB"
echo "IOS_APP_SIZE_REPORT=$REPORT_PATH"
echo "IOS_APP_SIZE_EXPORT_DIR=$EXPORT_DIR"
