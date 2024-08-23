load("@rules_rust//cargo:defs.bzl", "cargo_build_script")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")

bitdrift_rust_library(
    name = "platform-shared",
    srcs = glob(["src/**/*.rs"]),
    visibility = ["//visibility:public"],
    deps = [":build_script"],
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
