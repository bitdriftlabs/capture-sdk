load("@rules_cc//cc:defs.bzl", "cc_binary")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")

bitdrift_rust_library(
    name = "test_jni",
    testonly = True,
    extra_aliases = {"//platform/jvm:capture_jni": "capture"},
    deps = [
        "//platform/jvm:capture_jni",
        "//platform/shared:platform-shared",
        "//platform/test_helpers",
    ],
    alwayslink = True,
)

cc_binary(
    name = "capture",
    testonly = True,
    linkopts = [
        "-lm",  # Required to avoid dlopen runtime failures unrelated to rust
        "-lz",  # Link against system zlib library
    ],
    linkshared = True,
    visibility = ["//visibility:public"],
    deps = [
        ":test_jni",
        "//platform/jvm:capture_jni",
    ],
)
