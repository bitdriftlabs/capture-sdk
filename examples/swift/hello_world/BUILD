load("@build_bazel_rules_apple//apple:apple.bzl", "apple_static_xcframework_import")
load("@build_bazel_rules_apple//apple:ios.bzl", "ios_application")
load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")

genrule(
    name = "expanded_xcframework",
    srcs = ["//:ios_xcframework_with_rust_symbols"],
    outs = [
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/x86_64.swiftdoc",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftdoc",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/x86_64.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Capture",
        "Capture.xcframework/ios-arm64/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftinterface",
        "Capture.xcframework/ios-arm64/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftdoc",
        "Capture.xcframework/ios-arm64/Capture.framework/Capture",
        "Capture.xcframework/Info.plist",
    ],
    cmd = "unzip -o $< -d $(RULEDIR)",
    stamp = True,
    tags = ["local"],
)

apple_static_xcframework_import(
    name = "capture_xcframework",
    xcframework_imports = [":expanded_xcframework"],
)

swift_library(
    name = "ios_main",
    srcs = glob(["*.swift"]),
    tags = ["manual"],
    deps = [":capture_xcframework"],
)

ios_application(
    name = "ios_app",
    bundle_id = "io.bitdrift.example.helloworld",
    families = [
        "iphone",
        "ipad",
    ],
    infoplists = ["Info.plist"],
    minimum_os_version = "14.0",
    provisioning_profile = select({
        "//bazel:ios_device_build": "//bazel/ios:ios_provisioning_profile",
        "//conditions:default": None,
    }),
    tags = [
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [":ios_main"],
)

# App that directly imports Capture as a `swift_library` for compatibility with `rules_xcodeproj`
# and allowing building the app and all its deps in a single step.

swift_library(
    name = "ios_direct",
    srcs = glob(["*.swift"]),
    tags = ["manual"],
    deps = ["//platform/swift/source:ios_lib"],
)

ios_application(
    name = "hello_world_app",
    bundle_id = "io.bitdrift.example.helloworld",
    families = [
        "iphone",
        "ipad",
    ],
    infoplists = ["Info.plist"],
    minimum_os_version = "14.0",
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
