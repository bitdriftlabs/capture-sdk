load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")
load("//bazel/platforms:configured_test.bzl", "macos_test")

bitdrift_rust_library(
    name = "bd_unwind",
    visibility = ["//visibility:public"],
)

# Keep portable Linux coverage and run the same suite with Mach and PAC tests on Apple ARM64.
macos_test(
    name = "bd_unwind_apple_test",
    tags = ["macos_only"],
    target_compatible_with = select({
        "//bazel/platforms:mixed_rbe": [],
        "//conditions:default": ["@platforms//cpu:aarch64"],
    }),
    test = ":bd_unwind_test",
)
