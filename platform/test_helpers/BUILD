load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")

alias(
    name = "test_helpers",
    actual = "platform_test_helpers",
    visibility = ["//visibility:public"],
)

bitdrift_rust_library(
    name = "platform_test_helpers",
    deps = [
        "//platform/shared:platform-shared",
    ],
)
