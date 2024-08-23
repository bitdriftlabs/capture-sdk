#!/bin/bash

set -euxo pipefail

# Builds the logger benchmark for profiling, then generates a flamegraph chart of running it for 1 second.
# This relies on the flamegraph crate, which can be installed by invoking `cargo install flamegraph`
# and making sure the $HOME/.cargo/bin is on your PATH.
./bazelw build --config benchmark-profile //test/benchmark:logger_benchmark

if [[ $OSTYPE == 'linux'* ]]; then
  cmd=(flamegraph -v --no-inline -c "record --call-graph dwarf")
else # macos
  # dtrace requires root
  cmd=(sudo flamegraph)
fi

"${cmd[@]}" -o flame.svg -- ./bazel-bin/test/benchmark/logger_benchmark --bench --profile-time 1
