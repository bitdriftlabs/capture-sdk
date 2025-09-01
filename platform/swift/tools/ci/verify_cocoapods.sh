#!/bin/bash

set -euxo pipefail

echo "+++ Verify CocoaPods podspecs"

pod spec lint --verbose BitdriftCapture.podspec
