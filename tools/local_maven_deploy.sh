#!/bin/bash

# Builds Android aar and creates simlinks to it in a local maven
# repository so that the SDK can be picked by Grandle when 
# compiling Grandle example app.

set -euo pipefail

local_maven_repo="$HOME/.m2/repository"

mkdir -p "$local_maven_repo/io/bitdrift/capture/SNAPSHOT"

ln -fs "$(pwd)/bazel-bin/capture.aar" "$local_maven_repo/io/bitdrift/capture/SNAPSHOT/capture-SNAPSHOT.aar"
ln -fs "$(pwd)/bazel-bin/capture.pom" "$local_maven_repo/io/bitdrift/capture/SNAPSHOT/capture-SNAPSHOT.pom"
ln -fs "$(pwd)/bazel-bin/capture-javadoc.jar" "$local_maven_repo/io/bitdrift/capture/SNAPSHOT/capture-SNAPSHOT-javadoc.jar"
ln -fs "$(pwd)/bazel-bin/capture-sources.jar" "$local_maven_repo/io/bitdrift/capture/SNAPSHOT/capture-SNAPSHOT-sources.jar"

./bazelw build \
  --announce_rc \
  --fat_apk_cpu=arm64-v8a \
  --define=pom_version="SNAPSHOT" \
  //:capture_aar_with_artifacts
