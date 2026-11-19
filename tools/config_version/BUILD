load("@rules_shell//shell:sh_test.bzl", "sh_test")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary")

bitdrift_rust_binary(
    name = "config_version",
)

sh_test(
    name = "config_version_test",
    size = "small",
    srcs = ["config_version_test.sh"],
    args = ["$(rootpath :config_version)"],
    data = [":config_version"],
)
