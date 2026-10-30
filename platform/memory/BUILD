load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")
load("//bazel:config.bzl", "APPLE_TARGET_COMPATIBILITY")

bitdrift_rust_library(
    name = "bd_memory_dump",
    tags = [
        "macos_only",
    ],
    target_compatible_with = APPLE_TARGET_COMPATIBILITY,
    visibility = ["//visibility:public"],
)
