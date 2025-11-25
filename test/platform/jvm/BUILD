load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_shared_library")

bitdrift_rust_shared_library(
    name = "capture",
    testonly = True,
    visibility = ["//visibility:public"],
    deps = [
        "//platform/jvm/core:capture_core",
        "//platform/shared:platform-shared",
        "//platform/test_helpers",
    ],
)
