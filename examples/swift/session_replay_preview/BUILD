load("@rules_apple//apple:ios.bzl", "ios_application")
load("@rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "ios_direct",
    package_name = "BitdriftCapture",
    srcs = glob(["*.swift"]),
    tags = ["manual"],
    deps = ["//platform/swift/source:ios_lib"],
)

ios_application(
    name = "session_replay_preview_app",
    bundle_id = "io.bitdrift.example.sessionreplaypreviewapp",
    families = [
        "iphone",
        "ipad",
    ],
    infoplists = ["Info.plist"],
    minimum_os_version = "15.0",
    provisioning_profile = select({
        "//bazel:ios_device_build": "//bazel/ios:ios_provisioning_profile",
        "//conditions:default": None,
    }),
    tags = [
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [":ios_direct"],
)
