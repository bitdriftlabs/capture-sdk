load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_crate")

bitdrift_rust_crate(
    name = "platform-shared",
    crate_name = "platform_shared",
    srcs = glob(["src/**/*.rs"]),
    visibility = ["//visibility:public"],
    build_script_data = [":sdk_version"],
)

alias(
    name = "shared",
    actual = ":platform-shared",
    visibility = ["//visibility:public"],
)

bitdrift_rust_crate(
    name = "version_codegen",
    crate_name = "version_codegen",
    crate_srcs = glob(["version_codegen/**/*.rs"]),
    extra_binaries = ["//platform/shared:version_codegen"],
    visibility = ["//visibility:public"],
    build_script_enabled = False,
    library = False,
    binary = True,
    binary_crate_root = "version_codegen/main.rs",
    binary_srcs = glob(["version_codegen/**/*.rs"]),
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
