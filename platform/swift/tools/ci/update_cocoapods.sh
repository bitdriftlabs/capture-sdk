#!/bin/bash

set -euxo pipefail

readonly version="$1"

echo "+++ Generating podpsecs for $version version"

function update_podspec() {
  local -r pod_name="$1"
  local -r podspec="$pod_name".podspec
  local -r tmp_podspec=tmp_"$podspec"

  # Process '<name>.podspec' file:
  #  * Look for a `capture-<VERSION>/Capture.zip` string and substitute existig version for the new one.
  #  * Look for a `version = '<VERSION>'` string and substitute existig version for the new one.
  sed -e "s#\(capture-\)\(.*\)\(/Capture\.zip\)#\1$version\3#g" "$podspec" \
    | sed -e "s#\(version = '\)\(.*\)\('\)#\1$version\3#g" \
    > "$tmp_podspec"
  mv "$tmp_podspec" "$podspec"

  echo -e "\n++ Generated $podspec:\n"
  cat "$podspec"
}

podspecs=(\
  "BitdriftCapture" \
  "CaptureCocoaLumberjack" \
  "CaptureSwiftyBeaver" \
)

for podspec in "${podspecs[@]}"; do
  update_podspec "$podspec"
done
