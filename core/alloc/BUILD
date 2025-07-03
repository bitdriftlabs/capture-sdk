load("@rules_rust//rust:defs.bzl", "rust_library")

rust_library(
    name = "alloc",
    srcs = ["lib.rs"],
    edition = "2021",
    rustc_env = {
        # We pretend like we are bootstrping the compiler so rust allows us to use unstable
        # features while building with the stable compiler.
        "RUSTC_BOOTSTRAP": "1",
    },
    visibility = ["//visibility:public"],
)
