load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")

bitdrift_mobile_swift_test(
    name = "test",
    srcs = glob(
        include = ["**/*.swift"],
        exclude = ["integrations/URLSessionIntegrationTests.swift"],
    ),
    repository = "@capture",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:rust_bridge",
        "//test/platform/swift/benchmark:benchmarks",
        "//test/platform/swift/bridging:rust_bridge",
        "@Difference//:difference",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)

bitdrift_mobile_swift_test(
    name = "urlsession_integration_test",
    srcs = glob(["integrations/**/*.swift"]),
    repository = "@capture",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
    ],
)

# TODO(Augustyniak): Organize this code better.
# Separate target needed since TSAN gives false positives while running `URLSessionIntegrationTests` tests.
bitdrift_mobile_swift_test(
    name = "test_tsan",
    srcs = glob(
        include = ["**/*.swift"],
        exclude = ["integrations/URLSessionIntegrationTests.swift"],
    ),
    repository = "@capture",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:rust_bridge",
        "//test/platform/swift/benchmark:benchmarks",
        "//test/platform/swift/bridging:rust_bridge",
        "@Difference//:difference",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)
