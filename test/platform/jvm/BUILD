load("@rules_cc//cc:defs.bzl", "cc_binary")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library", "bitdrift_rust_shared_library")

bitdrift_rust_shared_library(
    name = "capture",
    testonly = True,
    visibility = ["//visibility:public"],
    deps = [
        "//platform/jvm:capture",
        "//platform/shared:platform-shared",
        "//platform/test_helpers",
    ],
)
