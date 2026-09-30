load("@rules_cc//cc:defs.bzl", "objc_library")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")

objc_library(
    name = "apple_crash_shim",
    srcs = ["apple_crash_shim.m"],
    hdrs = ["apple_crash_shim.h"],
    sdk_frameworks = ["Foundation"],
    visibility = ["//visibility:private"],
)

bitdrift_rust_library(
    name = "bd_crash_reporter",
    tags = [
        "macos_only",
    ],
    visibility = ["//visibility:public"],
    deps = [":apple_crash_shim"],
)
