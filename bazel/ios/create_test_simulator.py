# capture-sdk - bitdrift's client SDK
# Copyright Bitdrift, Inc. All rights reserved.
#
# Use of this source code is governed by a source available license that can be found in the
# LICENSE file or at:
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

"""Create or reuse a simulator dedicated to one Bazel test target."""

import hashlib
import os

from python.runfiles import runfiles


def main():
    files = runfiles.Create()
    creator = files.Rlocation(
        "rules_apple/apple/testing/default_runner/simulator_creator"
    )
    args = [creator]
    target = os.environ.get("TEST_TARGET")
    if target:
        target_id = hashlib.sha256(target.encode()).hexdigest()[:12]
        device = os.environ["SIMULATOR_DEVICE_TYPE"]
        version = os.environ["SIMULATOR_OS_VERSION"]
        args += ["--name", f"BAZEL_TEST_{device}_{version}_{target_id}"]

    os.execve(creator, args, os.environ | files.EnvVars())


if __name__ == "__main__":
    main()
