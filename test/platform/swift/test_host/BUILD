load("@build_bazel_rules_apple//apple:ios.bzl", "ios_application")
load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION")

ios_application(
    name = "TestHost",
    testonly = True,
    bundle_id = "io.bitdrift.HostApplication",
    families = ["iphone"],
    infoplists = ["Info.plist"],
    minimum_os_version = MINIMUM_IOS_VERSION,
    provisioning_profile = select({
        "//bazel:ios_device_build": "//bazel/ios:ios_provisioning_profile",
        "//conditions:default": None,
    }),
    tags = [
        "manual",
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [":ios_main"],
)

swift_library(
    name = "ios_main",
    srcs = glob(["Sources/*.swift"]),
    tags = ["manual"],
)
