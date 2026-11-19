load("@rules_pkg//:pkg.bzl", "pkg_tar")
load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")
load("//bazel:config.bzl", "APPLE_TARGET_COMPATIBILITY")
load("//bazel/ios:configured_artifacts.bzl", "ios_artifacts")

# These modules are linked into the benchmark XCTest bundle, never imported by SDK clients.
BENCHMARK_SWIFT_FEATURES = [
    "-swift.emit_swiftinterface",
    "-swift.enable_library_evolution",
    "swift.enable_testing",
]

swift_library(
    name = "benchmarks",
    testonly = True,
    srcs = ["ClockTimeProfiler.swift"],
    features = BENCHMARK_SWIFT_FEATURES,
    module_name = "LoggerBenchmarks",
    tags = [
        "macos_only",
        "manual",
    ],
    target_compatible_with = APPLE_TARGET_COMPATIBILITY,
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:CapturePassable",
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:objc_bridge",
        "//test/platform/swift/bridging:rust_bridge",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)

bitdrift_mobile_swift_test(
    name = "run_benchmarks",
    srcs = ["RunLoggerBenchmarkTests.swift"],
    swift_features = BENCHMARK_SWIFT_FEATURES,
    tags = [
        # ensure no other tests running when benchmarking
        "exclusive",
        "macos_only",
        "manual",
        "requires_test_host",
    ],
    use_test_host = True,
    visibility = ["//visibility:public"],
    deps = [
        ":benchmarks",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)

# Package the XCTest launcher and its runfiles so hosted CI can run the benchmark
# with Xcode without invoking Bazel.
pkg_tar(
    name = "benchmark_runner",
    testonly = True,
    srcs = [":run_benchmarks_xctest"],
    extension = "tar.gz",
    include_runfiles = True,
    preserve_mode = True,
    strip_prefix = ".",
    tags = ["manual"],
)

ios_artifacts(
    name = "ios_benchmark_runner_artifact",
    testonly = True,
    srcs = [":benchmark_runner"],
    cpu = "sim_arm64",
    # The directly linked SDK and XCTest bundle otherwise both provide Info.plist.
    tags = ["manual"],
    test_bundle = True,
)
