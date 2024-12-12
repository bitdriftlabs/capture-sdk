#!/bin/bash
# This script is used to release the packages in the monorepo
# It was meant to be run from CI but you can run it locally as:
#
# $ TARGETS="@bitdrift/core,@bitdrift/electron" VERSION="minor" ./release.sh

set -e

# Set the proper github user for commits; conditionally run in github actions
if [ "$CI" = "true" ]; then
  git config user.name github-actions
  git config user.email github-actions@github.com

  # Setup the npm registry for publishing
  echo "//registry.npmjs.org/:_authToken=$NPM_TOKEN" >> ~/.npmrc
fi

# Map the project names to the package.json files
TARGETS=$(tr -d ' ' <<< $TARGETS)
TARGET_PATHS=$(sed "s/@bitdrift\//packages\//g" <<< $TARGETS | tr ',' ' ')

# Update the version in the package(s).json files first, note
# this might modify other projects that depend on the updating target.
#
# We'll not commit those projects changes
echo "Updating version to $VERSION"
npm exec nx release version $VERSION -- -p $TARGETS --stage-changes=false

sleep 2
echo "Giving nx some time to charge the flux capacitor..."

# Prepare for release (executes anything that the target needs to do)
echo "Preparing release (e.g. update/upload platform specific on core)"
npm exec nx run-many -- -t prepare-release -p $TARGETS
npm install --package-lock-only --silent

# Do the release with the versions previously updated
npm exec nx release publish -- -p $TARGETS -y

# Stage all relevant project files but nothing more
echo "Staging changes for $TARGET_PATHS"
xargs git add <<< $TARGET_PATHS
git add package-lock.json package.json

# Push git changes made by the version update script to remote
git commit -m "chore: release $VERSION" -m "Packages: $TARGETS"
git push origin
