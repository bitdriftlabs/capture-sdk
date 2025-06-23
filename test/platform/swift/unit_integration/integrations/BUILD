load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")

bitdrift_mobile_swift_test(
    name = "test",
    srcs = glob(["**/*.swift"]),
    repository = "@capture",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//test/platform/swift/unit_integration/mocks",
    ],
)
