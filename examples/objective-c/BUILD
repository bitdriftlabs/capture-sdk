load("@rules_apple//apple:ios.bzl", "ios_application")
load("@rules_cc//cc:defs.bzl", "objc_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION")

objc_library(
    name = "ios_direct",
    srcs = glob([
        "*.h",
        "*.m",
    ]),
    linkopts = ["-Wl,-all_load"],
    tags = ["manual"],
    deps = ["//:capture_apple_static_framework_import"],
)

ios_application(
    name = "hello_world_app",
    bundle_id = "io.bitdrift.example.objc.helloworld",
    families = [
        "iphone",
        "ipad",
    ],
    infoplists = ["Info.plist"],
    minimum_os_version = MINIMUM_IOS_VERSION,
    provisioning_profile = select({
        "//bazel:ios_device_build": "//bazel/ios:ios_provisioning_profile",
        "//conditions:default": None,
    }),
    tags = [
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "ios_direct",
    ],
)
