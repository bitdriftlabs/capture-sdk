config_setting(
    name = "dbg_build",
    values = {"compilation_mode": "dbg"},
)

config_setting(
    name = "opt_build",
    values = {"compilation_mode": "opt"},
)

config_setting(
    name = "linux",
    constraint_values = [
        "@platforms//os:linux",
    ],
    visibility = ["//visibility:public"],
)

alias(
    name = "zipper",
    actual = "@bazel_tools//tools/zip:zipper",
    visibility = ["//visibility:public"],
)

exports_files([
    "pom_template.xml",
    "run_fuzzer.sh",
    "test_fuzzer.sh",
])

config_setting(
    name = "ios_device_build",
    values = {
        "cpu": "ios_arm64",
    },
)

#==== Platform definitions that are used in the 'platform_mappings' file
platform(
    name = "android_aarch64",
    constraint_values = [
        "@platforms//cpu:aarch64",
        "@platforms//os:android",
    ],
)

platform(
    name = "android_armeabi",
    constraint_values = [
        "@platforms//cpu:armv7",
        "@platforms//os:android",
    ],
)

platform(
    name = "android_x86",
    constraint_values = [
        "@platforms//cpu:x86_32",
        "@platforms//os:android",
    ],
)

platform(
    name = "android_x86_64",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:android",
    ],
)

platform(
    name = "macos_x86_64",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:macos",
    ],
)

platform(
    name = "macos_arm64",
    constraint_values = [
        "@platforms//cpu:arm64",
        "@platforms//os:macos",
    ],
)

platform(
    name = "ios_x86_64",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:ios",
        "@build_bazel_apple_support//constraints:simulator",
    ],
)

platform(
    name = "ios_sim_arm64",
    constraint_values = [
        "@platforms//cpu:arm64",
        "@platforms//os:ios",
        "@build_bazel_apple_support//constraints:simulator",
    ],
)

platform(
    name = "ios_arm64",
    constraint_values = [
        "@platforms//cpu:arm64",
        "@platforms//os:ios",
        "@build_bazel_apple_support//constraints:device",
    ],
)
