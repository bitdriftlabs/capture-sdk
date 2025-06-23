load("@rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "mocks",
    testonly = True,
    srcs = glob(["**/*.swift"]),
    features = [
        "swift.enable_library_evolution",
    ],
    module_name = "CaptureMocks",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//platform/swift/source:objc_bridge",
    ],
)
