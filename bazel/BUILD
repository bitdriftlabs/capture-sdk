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
