#!/bin/bash

# fail job on shell command or pipe failure
#
# -e exit immediately on pipeline failure
# -u treat unset variables or params as an error
# -x print trace of commands
# -o set option
# pipefail: pipeline return value is first nonzero value
set -euxo pipefail

# Pod list subset containing BitdriftCapture
CDN_PATH="https://cdn.cocoapods.org/all_pods_versions_0_7_9.txt"

# Current release version number
VERSION=$(cat BitdriftCapture.podspec | grep "s.version = '" | sed "s/.*s.version = '\(.*\)'/\1/")

# Check if the latest version is present
curl --silent "$CDN_PATH" \
    | grep --quiet "BitdriftCapture.*/$VERSION/"
