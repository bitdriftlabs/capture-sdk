load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")

bitdrift_mobile_swift_test(
    name = "test",
    srcs = glob(["**/*.swift"]),
    repository = "@capture",
    tags = ["macos_only"],
    use_test_host = False,
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//test/platform/swift/unit_integration/mocks",
    ],
)
