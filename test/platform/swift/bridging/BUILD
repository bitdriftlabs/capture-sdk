load("@rules_swift//swift:swift.bzl", "swift_library")
load("@rules_cc//cc:defs.bzl", "cc_library", "objc_library")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")

swift_library(
    name = "CaptureTestBridging",
    testonly = True,
    srcs = glob(["**/*.swift"]),
    features = [
        "swift.enable_library_evolution",
    ],
    generates_header = True,
    module_name = "CaptureTestBridging",
    tags = [
        "macos_only",
        "manual",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:CapturePassable",
    ],
)

objc_library(
    name = "rust_bridge",
    testonly = True,
    hdrs = [
        "CaptureTestBridge.h",
    ],
    module_name = "CaptureTestBridge",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        ":CaptureTestBridging",
        ":logger_test_bridge",
    ],
)

# objc_library doesn't let us depend directly on a rust lib, so add this indirection.
cc_library(
    name = "logger_test_bridge",
    testonly = True,
    tags = ["macos_only"],
    deps = [
        ":test_swift_bridge",
        "//platform/test_helpers",
    ],
    alwayslink = True,
)

bitdrift_rust_library(
    name = "test_swift_bridge",
    testonly = True,
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/swift/source:swift_bridge",
        "//platform/test_helpers",
    ],
)
