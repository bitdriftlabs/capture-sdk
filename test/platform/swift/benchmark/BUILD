load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")

swift_library(
    name = "benchmarks",
    testonly = True,
    srcs = ["ClockTimeProfiler.swift"],
    module_name = "LoggerBenchmarks",
    tags = [
        "macos_only",
        "manual",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:objc_bridge",
        "//platform/swift/source:CapturePassable",
        "//platform/swift/source:ios_lib",
        "//test/platform/swift/bridging:rust_bridge",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)

bitdrift_mobile_swift_test(
    name = "run_benchmarks",
    srcs = ["RunLoggerBenchmarkTests.swift"],
    tags = [
        # ensure no other tests running when benchmarking
        "exclusive",
        "macos_only",
        "requires_test_host",
    ],
    use_test_host = True,
    visibility = ["//visibility:public"],
    deps = [
        ":benchmarks",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)
