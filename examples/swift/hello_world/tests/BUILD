load("//bazel:bitdrift_swift_test.bzl", "bitdrift_mobile_swift_test")

bitdrift_mobile_swift_test(
    name = "test",
    srcs = [
        "E2EStartupConfigurationTests.swift",
        "//examples/swift/hello_world:E2EStartupConfiguration.swift",
    ],
    tags = ["macos_only"],
)
