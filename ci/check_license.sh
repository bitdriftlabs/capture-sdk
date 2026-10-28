#!/bin/bash

set -e

python3 ./ci/license_header.py --check "$@"
