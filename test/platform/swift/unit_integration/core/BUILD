load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")

bitdrift_mobile_swift_test(
    name = "test",
    srcs = glob(["**/*.swift"]) + ["//proto:report_swift_source"],
    data = glob(["testdata/*"]),
    parallel = True,  # The iOS 27 suite uses a different simulator runtime.
    repository = "@capture",
    use_test_host = True,
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:bitdrift_enhanced_crash_data",
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:objc_bridge",
        "//test/platform/swift/benchmark:benchmarks",
        "//test/platform/swift/bridging:rust_bridge",
        "//test/platform/swift/unit_integration/mocks",
        "@Difference//:difference",
        "@SwiftBenchmark//:swift_benchmark",
        "@flatbuffers//swift",
    ],
)

bitdrift_mobile_swift_test(
    name = "ios27_test",
    srcs = [
        "reports/CrashReporterServiceTests.swift",
        "reports/MetricKitDiagnosticManagerTests.swift",
        "reports/MetricKitLegacyCrashAdapterTests.swift",
    ],
    # Build the release framework shape with the same Xcode selection as this test.
    build_data = ["//platform/swift/source:capture_ios_static_framework"],
    data = glob(["testdata/*"]),
    parallel = True,
    repository = "@capture",
    simulator = "27.0",
    target_compatible_with = select({
        "//bazel/platforms:ios27_tests": [],
        "//conditions:default": ["@platforms//:incompatible"],
    }),
    use_test_host = True,
    visibility = ["//visibility:public"],
    xcode = "27.0",
    deps = [
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:objc_bridge",
        "//test/platform/swift/unit_integration/mocks",
    ],
)
