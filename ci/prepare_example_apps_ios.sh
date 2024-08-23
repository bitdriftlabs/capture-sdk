#!/bin/bash

set -euo pipefail

echo "+++ Building iOS Hello World Example App"

mkdir -p dist

./bazelw build \
  --announce_rc \
  --config=ci \
  --config=dbg-ios \
  //examples/swift/hello_world:hello_world_app --config=dbg-ios

sdk_repo="$(pwd)"
output="$(mktemp -d)"

pushd "$(mktemp -d)"
  unzip "$sdk_repo/bazel-bin/examples/swift/hello_world/hello_world_app.ipa"
  mv "Payload/hello_world_app.app" "$output/ios_hello_world_app.app"
popd

echo "+++ Building iOS Session Replay Previos Example App"

./bazelw build \
  --announce_rc \
  --config=ci \
  --config=dbg-ios \
  //examples/swift/session_replay_preview:session_replay_preview_app --config=dbg-ios

sdk_repo="$(pwd)"
pushd "$(mktemp -d)"
  unzip "$sdk_repo/bazel-bin/examples/swift/session_replay_preview/session_replay_preview_app.ipa"
  mv "Payload/session_replay_preview_app.app" "$output/ios_session_replay_preview_app.app"
popd

echo "+++ Bundling iOS Example apps"

pushd "$output"
zip -r ios_example_apps.zip \
 ios_hello_world_app.app \
 ios_session_replay_preview_app.app
popd

rm -rf dist/ios_example_apps.zip
mv "$output"/ios_example_apps.zip dist/example-apps.ios.zip
