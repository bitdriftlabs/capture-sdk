./bazelw build //test/benchmark:live_benchmark --config benchmark
rm -rf target/criterion

./bazel-bin/test/benchmark/live_benchmark --bench
if [[ -z "${BITDRIFT_STAGGING_URL}" ]]; then
  echo "BITDRIFT_STAGGING_URL is not set. Skipping live_benchmark on staging."
  exit 1;
else
  BITDRIFT_URL=${BITDRIFT_STAGGING_URL} ./bazel-bin/test/benchmark/live_benchmark --bench
fi

open target/criterion/report/index.html
