load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_objc_test")

bitdrift_mobile_swift_objc_test(
    name = "test",
    hdrs_objc = glob(["**/*.h"]),
    srcs_objc = glob(["**/*.m"]),
    srcs_swift = glob(["**/*.swift"]),
    repository = "@capture",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:objc_bridge",
        "//test/platform/swift/benchmark:benchmarks",
        "//test/platform/swift/bridging:rust_bridge",
        "//test/platform/swift/unit_integration/mocks",
        "@Difference//:difference",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)
