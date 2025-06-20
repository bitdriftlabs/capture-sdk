load("@rules_rust//cargo:defs.bzl", "cargo_build_script")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary", "bitdrift_rust_library")

bitdrift_rust_library(
    name = "platform-shared",
    srcs = glob(["src/**/*.rs"]),
    visibility = ["//visibility:public"],
    deps = [":build_script"],
)

bitdrift_rust_binary(
    name = "version_codegen",
    srcs = glob(["version_codegen/**/*.rs"]),
    visibility = ["//visibility:public"],
)

cargo_build_script(
    name = "build_script",
    srcs = ["build.rs"],
    data = [":sdk_version"],
    edition = "2021",
)

filegroup(
    name = "sdk_version",
    srcs = [".sdk_version"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "version_kt_source",
    srcs = [":sdk_version"],
    outs = ["BuildConstants.kt"],
    cmd = "$(location :version_codegen) $(location :sdk_version) > $(OUTS)",
    tools = [":version_codegen"],
    visibility = ["//visibility:public"],
)
