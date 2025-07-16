load("@rules_apple//apple:apple.bzl", "local_provisioning_profile")
load("@rules_shell//shell:sh_binary.bzl", "sh_binary")
load(
    "@rules_xcodeproj//xcodeproj:defs.bzl",
    "xcode_provisioning_profile",
)

exports_files(["symbols_to_rewrite"])

sh_binary(
    name = "rewrite_symbols",
    srcs = [
        "rewrite_symbols.sh",
    ],
    visibility = ["//visibility:public"],
)

config_setting(
    name = "produce_framework_plist",
    values = {"define": "ios_produce_framework_plist=true"},
)

# Bitdrift team ID
TEAM_ID = "33XQXT255C"

# If needed, change to the name shown by clicking the (i) next to
# "Xcode Managed Profile" in the "Signing and Capabilities" tab
PROFILE_NAME = "Wildcard"

xcode_provisioning_profile(
    name = "ios_provisioning_profile",
    managed_by_xcode = False,
    provisioning_profile = ":xcode_managed_profile",
    profile_name = PROFILE_NAME,
    tags = ["manual"],
    visibility = ["//visibility:public"],
)

local_provisioning_profile(
    name = "xcode_managed_profile",
    profile_name = PROFILE_NAME,
    tags = ["manual"],
    team_id = TEAM_ID,
)
