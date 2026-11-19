load("@build_bazel_apple_support//xcode:xcode_config.bzl", "xcode_config")
load("@build_bazel_apple_support//xcode:xcode_version.bzl", "xcode_version")

# BuildBuddy remote executor for --config=rbe. It matches the amd64 BuildBuddy Workflows runner
# that drives Bazel, so host tools fetched during analysis (the hermetic LLVM, Android SDK/NDK and
# Rust toolchains) also run on the executor. The Android NDK only ships x86_64 Linux host tools.
# The C/C++ toolchain comes from --config=linux-hermetic-llvm; the image supplies the shell
# utilities that actions run with and the ld that --config=libunwind expects under /usr/bin.
platform(
    name = "buildbuddy_linux_x86_64",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    exec_properties = {
        "Arch": "amd64",
        "OSFamily": "Linux",
        # BuildBuddy's Ubuntu 24.04 RBE image, pinned by digest so remote cache keys change with
        # the image.
        "container-image": "docker://gcr.io/flame-public/rbe-ubuntu24-04@sha256:be9853fc45092d2cf2bda3abed30d22182881659a50bf35c7125161d7cfc8a75",
    },
    visibility = ["//visibility:public"],
)

platform(
    name = "buildbuddy_macos_arm64",
    exec_properties = {
        "Arch": "arm64",
        "OSFamily": "darwin",
        "Pool": "default",
        "env-overrides": "PATH=/usr/bin:/bin:/usr/sbin:/sbin",
        "use-self-hosted-executors": "true",
    },
    parents = ["@build_bazel_apple_support//platforms:macos_arm64"],
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mixed_rbe",
    define_values = {"capture_mixed_rbe": "true"},
    visibility = ["//visibility:public"],
)

xcode_version(
    name = "xcode_26",
    aliases = ["26.0.1"],
    default_ios_sdk_version = "26.0",
    default_macos_sdk_version = "26.0",
    version = "26.0.1.17A400",
)

xcode_version(
    name = "xcode_27",
    aliases = ["27.0"],
    default_ios_sdk_version = "27.0",
    default_macos_sdk_version = "27.0",
    version = "27.0.0.27A266a",
)

xcode_config(
    name = "executor_xcodes",
    default = ":xcode_26",
    versions = [
        ":xcode_26",
        ":xcode_27",
    ],
    visibility = ["//visibility:public"],
)
