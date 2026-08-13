load("@ios_signing_overrides//:signing_overrides.bzl", "PROFILE_NAME", "TEAM_ID")
load("@rules_apple//apple:apple.bzl", "local_provisioning_profile")
load("@rules_shell//shell:sh_binary.bzl", "sh_binary")
load(
    "@rules_xcodeproj//xcodeproj:defs.bzl",
    "xcode_provisioning_profile",
)

sh_binary(
    name = "rewrite_symbols",
    srcs = [
        "rewrite_symbols.sh",
    ],
    visibility = ["//visibility:public"],
)

alias(
    name = "llvm_ar",
    actual = select({
        "@llvm//platforms/config:macos_aarch64_prebuilt": "@llvm-toolchain-minimal-22.1.7-darwin-arm64//:bin/llvm-ar",
        "@llvm//platforms/config:macos_x86_64_prebuilt": "@llvm-toolchain-minimal-22.1.7-darwin-amd64//:bin/llvm-ar",
    }),
    visibility = ["//visibility:public"],
)

alias(
    name = "llvm_lipo",
    actual = select({
        "@llvm//platforms/config:macos_aarch64_prebuilt": "@llvm-toolchain-minimal-22.1.7-darwin-arm64//:bin/llvm-lipo",
        "@llvm//platforms/config:macos_x86_64_prebuilt": "@llvm-toolchain-minimal-22.1.7-darwin-amd64//:bin/llvm-lipo",
    }),
    visibility = ["//visibility:public"],
)

alias(
    name = "llvm_ranlib",
    actual = select({
        "@llvm//platforms/config:macos_aarch64_prebuilt": "@llvm-toolchain-minimal-22.1.7-darwin-arm64//:bin/llvm-ranlib",
        "@llvm//platforms/config:macos_x86_64_prebuilt": "@llvm-toolchain-minimal-22.1.7-darwin-amd64//:bin/llvm-ranlib",
    }),
    visibility = ["//visibility:public"],
)

config_setting(
    name = "produce_framework_plist",
    values = {"define": "ios_produce_framework_plist=true"},
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
