load("@ios_signing_overrides//:signing_overrides.bzl", "PROFILE_NAME", "TEAM_ID")
load("@rules_apple//apple:apple.bzl", "local_provisioning_profile")
load("@rules_rust//rust:toolchain.bzl", "rust_toolchain")
load(
    "@rules_xcodeproj//xcodeproj:defs.bzl",
    "xcode_provisioning_profile",
)

alias(
    name = "llvm_ar",
    actual = select({
        "@llvm//platforms/config:macos_aarch64": "@llvm-toolchain-minimal-darwin-arm64//:bin/llvm-ar",
        "@llvm//platforms/config:macos_x86_64": "@llvm-toolchain-minimal-darwin-amd64//:bin/llvm-ar",
    }),
    visibility = ["//visibility:public"],
)

alias(
    name = "llvm_lipo",
    actual = select({
        "@llvm//platforms/config:macos_aarch64": "@llvm-toolchain-minimal-darwin-arm64//:bin/llvm-lipo",
        "@llvm//platforms/config:macos_x86_64": "@llvm-toolchain-minimal-darwin-amd64//:bin/llvm-lipo",
    }),
    visibility = ["//visibility:public"],
)

alias(
    name = "llvm_ranlib",
    actual = select({
        "@llvm//platforms/config:macos_aarch64": "@llvm-toolchain-minimal-darwin-arm64//:bin/llvm-ranlib",
        "@llvm//platforms/config:macos_x86_64": "@llvm-toolchain-minimal-darwin-amd64//:bin/llvm-ranlib",
    }),
    visibility = ["//visibility:public"],
)

config_setting(
    name = "produce_framework_plist",
    values = {"define": "ios_produce_framework_plist=true"},
)

# Rust host tools retain the ordinary host sysroot. Only iOS Simulator targets
# select the pinned TSan sysroot published by rust-std-mobile.
config_setting(
    name = "ios_tsan",
    define_values = {"ios_tsan": "true"},
)

rust_toolchain(
    name = "ios_tsan_rust_toolchain_impl",
    allocator_library = None,
    binary_ext = "",
    cargo = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:cargo",
    cargo_clippy = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:cargo_clippy_bin",
    clippy_driver = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:clippy_driver_bin",
    default_edition = "",
    dylib_ext = ".dylib",
    exec_triple = "aarch64-apple-darwin",
    extra_exec_rustc_flags = [],
    extra_rustc_flags = [
        "-Zunstable-options",
        "-C",
        "panic=immediate-abort",
        "-Zsanitizer=thread",
        "-Zexternal-clangrt",
    ],
    global_allocator_library = None,
    linker = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:rust-lld",
    linker_type = "direct",
    llvm_cov = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:llvm_cov_bin",
    llvm_lib = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:llvm_lib",
    llvm_profdata = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:llvm_profdata_bin",
    process_wrapper = "@rules_rust//util/process_wrapper",
    rust_doc = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:rustdoc",
    rust_std = "@rust_std_tsan_aarch64_apple_ios_sim//:rust_std",
    rustc = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:rustc",
    rustc_lib = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:rustc_lib",
    rustfmt = "@rust_macos_aarch64__aarch64-apple-ios-sim__stable_tools//:rustfmt_bin",
    staticlib_ext = ".a",
    stdlib_linkflags = [
        "-lSystem",
        "-lobjc",
        "-Wl,-framework,Security",
        "-Wl,-framework,Foundation",
        "-lresolv",
    ],
    target_triple = "aarch64-apple-ios-sim",
    visibility = ["//visibility:public"],
)

toolchain(
    name = "ios_tsan_rust_toolchain",
    exec_compatible_with = [
        "@platforms//cpu:aarch64",
        "@platforms//os:osx",
    ],
    target_compatible_with = [
        "@platforms//cpu:aarch64",
        "@platforms//os:ios",
        "@build_bazel_apple_support//constraints:simulator",
    ],
    target_settings = [
        ":ios_tsan",
        "@rules_rust//rust/private:bootstrapped",
        "@rules_rust//rust/toolchain/channel:stable",
    ],
    toolchain = ":ios_tsan_rust_toolchain_impl",
    toolchain_type = "@rules_rust//rust:toolchain",
    visibility = ["//visibility:public"],
)

# Defaults come from bazel/ios/signing_overrides.bzl and can be overridden locally via:
#   build --repo_env=APPLE_TEAM_ID=YOURTEAMID
#   build --repo_env=APPLE_PROFILE_NAME="Your Provisioning Profile Name"
# in .bazelrc.local.

xcode_provisioning_profile(
    name = "ios_provisioning_profile",
    managed_by_xcode = False,
    profile_name = PROFILE_NAME,
    provisioning_profile = ":xcode_managed_profile",
    tags = ["manual"],
    visibility = ["//visibility:public"],
)

local_provisioning_profile(
    name = "xcode_managed_profile",
    profile_name = PROFILE_NAME,
    tags = ["manual"],
    team_id = TEAM_ID,
)
