load("@rules_rs//rs:cargo_build_script.bzl", "cargo_build_script")
load("@rules_rust//rust:defs.bzl", "rust_test")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary", "bitdrift_rust_library")
load("//bazel/platforms:configured_test.bzl", "anyhow_no_backtrace_test", "ci_clippy_check")

bitdrift_rust_library(
    name = "platform-shared",
    srcs = glob(["src/**/*.rs"]),
    visibility = ["//visibility:public"],
    deps = [":build_script"],
)

anyhow_no_backtrace_test(
    name = "anyhow_no_backtrace_test",
    test = ":anyhow_error_test",
)

rust_test(
    name = "anyhow_error_test",
    size = "small",
    srcs = ["src/error_test.rs"],
    crate_root = "src/error_test.rs",
    edition = "2024",
    tags = ["manual"],
    deps = ["@crates//:anyhow"],
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
    edition = "2024",
    tags = ["clippy"],
)

ci_clippy_check(
    name = "platform_shared_build_script",
    deps = [":build_script_"],
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
