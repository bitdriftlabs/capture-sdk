#!/bin/bash

set -euo pipefail

# Changes to these files can alter which Clippy checks run without changing an
# ordinary Bazel target hash, so callers must run their full Clippy slice.
exec ./ci/files_changed.sh --files \
  .bazelrc \
  .clippy.toml \
  bazel/bitdrift_build_system.bzl \
  ci/check_bazel.sh \
  ci/check_clippy_config.sh \
  ci/files_changed.sh \
  ci/run_clippy.sh \
  ci/run_bazel_tests.sh \
  MODULE.bazel \
  MODULE.bazel.lock \
  .github/actions/check-clippy-config/action.yaml \
  .github/actions/files-changed/action.yaml \
  .github/actions/run-clippy/action.yaml \
  .github/actions/run-bazel-tests/action.yaml
