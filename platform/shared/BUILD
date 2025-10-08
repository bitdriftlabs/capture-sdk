load("@rules_rust//cargo:defs.bzl", "cargo_build_script")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary", "bitdrift_rust_library")

DEFAULT_VERSION = "10.0.0-dev"

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

genrule(
    name = "sdk_version",
    outs = [".sdk_version"],
    stamp = True,
    cmd = (
        "awk '$$1==\"BUILD_EMBED_LABEL\"{v=$$2} " +
        "END{printf \"%s\", (v ? v : \"" + DEFAULT_VERSION + "\")}' " +
        "bazel-out/stable-status.txt >$@"
    ),
    visibility = ["//visibility:private"],
)

genrule(
    name = "version_kt_source",
    srcs = [":sdk_version"],
    outs = ["BuildConstants.kt"],
    cmd = "$(location :version_codegen) $(location :sdk_version) > $(OUTS)",
    tools = [":version_codegen"],
    visibility = ["//visibility:public"],
)
