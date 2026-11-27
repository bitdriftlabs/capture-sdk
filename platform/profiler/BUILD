load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")
load("//bazel/platforms:configured_test.bzl", "macos_test")

bitdrift_rust_library(
    name = "profiler",
    visibility = ["//visibility:public"],
    deps = ["//platform/unwind:bd_unwind"],
)

# Mach capture and suspend/resume tests require an Apple ARM64 executor.
macos_test(
    name = "profiler_apple_test",
    tags = ["macos_only"],
    target_compatible_with = select({
        "//bazel/platforms:mixed_rbe": [],
        "//conditions:default": ["@platforms//cpu:aarch64"],
    }),
    test = ":profiler_test",
)
