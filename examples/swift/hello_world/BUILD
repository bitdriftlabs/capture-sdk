load("@rules_apple//apple:apple.bzl", "apple_static_xcframework_import")
load("@rules_apple//apple:ios.bzl", "ios_application")
load("@rules_apple//apple:versioning.bzl", "apple_bundle_version")
load("@rules_cc//cc:defs.bzl", "objc_library")
load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION")

APP_ICONS = glob(["AppAssets.xcassets/AppIcon.appiconset/**"])

APP_ICONS = select({
    "//bazel:is_ci": [],
    "//conditions:default": APP_ICONS,
})

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
    cmd = "rsync -a $< $(RULEDIR)",
)

apple_static_xcframework_import(
    name = "capture_xcframework",
    xcframework_imports = [":expanded_xcframework"],
)

apple_bundle_version(
    name = "hello_world_version",
    build_label_pattern = "HelloWorld_{version}_build_{build}",
    build_version = "{build}",
    capture_groups = {
        "build": "\\d+",
        "version": "\\d+\\.\\d+\\.\\d+",
    },
    fallback_build_label = "HelloWorld_0.1.0_build_1",
    short_version_string = "{version}",
)

objc_library(
    name = "crash_support",
    srcs = ["CrashPanel/CrashSupport.mm"],
    hdrs = ["CrashPanel/CrashSupport.h"],
    module_name = "HelloWorldCrashSupport",
    tags = ["manual"],
)

swift_library(
    name = "ios_main",
    srcs = glob(["**/*.swift"]),
    tags = ["manual"],
    deps = [
        ":capture_xcframework",
        ":crash_support",
    ],
)

ios_application(
    name = "ios_app",
    app_icons = APP_ICONS,
    bundle_id = "io.bitdrift.example.swiftapp.helloworld",
    bundle_name = "Bitdrift Sample App",
    entitlements = "Protected.entitlements",
    executable_name = "hello_world_app",
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
    version = ":hello_world_version",
    visibility = ["//visibility:public"],
    deps = [":ios_main"],
)

# App that directly imports Capture as a `swift_library` for compatibility with `rules_xcodeproj`
# and allowing building the app and all its deps in a single step.

swift_library(
    name = "ios_direct",
    srcs = glob(["**/*.swift"]),
    tags = ["manual"],
    deps = [
        ":crash_support",
        "//platform/swift/source:ios_lib",
    ],
)

ios_application(
    name = "hello_world_app",
    app_icons = APP_ICONS,
    bundle_id = "io.bitdrift.example.swiftapp.helloworld",
    bundle_name = "Bitdrift Sample App",
    entitlements = "Protected.entitlements",
    executable_name = "hello_world_app",
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
    version = ":hello_world_version",
    visibility = ["//visibility:public"],
    deps = [":ios_direct"],
)
