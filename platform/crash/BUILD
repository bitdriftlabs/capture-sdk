load("@rules_cc//cc:defs.bzl", "objc_library")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")
load("//bazel:config.bzl", "APPLE_TARGET_COMPATIBILITY")

objc_library(
    name = "apple_crash_shim",
    srcs = ["apple_crash_shim.m"],
    hdrs = ["apple_crash_shim.h"],
    sdk_frameworks = ["Foundation"],
    target_compatible_with = APPLE_TARGET_COMPATIBILITY,
    visibility = ["//visibility:private"],
)

bitdrift_rust_library(
    name = "bd_crash_reporter",
    tags = [
        "macos_only",
    ],
    target_compatible_with = APPLE_TARGET_COMPATIBILITY,
    visibility = ["//visibility:public"],
    deps = [":apple_crash_shim"],
)
