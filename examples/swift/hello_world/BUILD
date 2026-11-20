load("@rules_apple//apple:apple.bzl", "apple_static_xcframework_import")
load("@rules_apple//apple:ios.bzl", "ios_application")
load("@rules_apple//apple:versioning.bzl", "apple_bundle_version")
load("@rules_apple//apple:xcarchive.bzl", "xcarchive")
load("@rules_cc//cc:defs.bzl", "objc_library")
load("@rules_shell//shell:sh_test.bzl", "sh_test")
load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION")
load("//bazel/ios:configured_artifacts.bzl", "ios_artifacts")

APP_ICONS = glob(["AppAssets.xcassets/AppIcon.appiconset/**"])

CI_APP_ICONS = select({
    "//bazel:is_ci": [],
    "//conditions:default": APP_ICONS,
})

exports_files(
    ["E2EStartupConfiguration.swift"],
    visibility = ["//examples/swift/hello_world/tests:__pkg__"],
)

# The XCFramework declares its own slices. Use the same CPU setting as the
# device release build before those splits so the SDK compile actions can share.
ios_artifacts(
    name = "distribution_xcframework",
    srcs = ["//:ios_xcframework_for_distribution"],
    cpu = "arm64",
    tags = ["manual"],
)

genrule(
    name = "expanded_xcframework",
    srcs = [":distribution_xcframework"],
    outs = [
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/x86_64.swiftdoc",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/arm64.private.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftdoc",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/x86_64.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/x86_64.private.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Capture",
        "Capture.xcframework/ios-arm64/Capture.framework/Modules/Capture.swiftmodule/arm64.swiftinterface",
        "Capture.xcframework/ios-arm64/Capture.framework/Modules/Capture.swiftmodule/arm64.private.swiftinterface",
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
    # The XCFramework import only supplies the public Swift interface to swiftc.
    # The demo uses SDK SPI and the compiler needs its private companion as an input.
    swiftc_inputs = [
        "Capture.xcframework/ios-arm64/Capture.framework/Modules/Capture.swiftmodule/arm64.private.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/arm64.private.swiftinterface",
        "Capture.xcframework/ios-arm64_x86_64-simulator/Capture.framework/Modules/Capture.swiftmodule/x86_64.private.swiftinterface",
    ],
    tags = ["manual"],
    deps = [
        ":capture_xcframework",
        ":crash_support",
    ],
)

ios_application(
    name = "ios_app",
    app_icons = CI_APP_ICONS,
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
    version = ":hello_world_version",
    visibility = ["//visibility:public"],
    deps = [":ios_main"],
)

ios_application(
    name = "ios_app_size",
    app_icons = CI_APP_ICONS,
    bundle_id = "io.bitdrift.example.swiftapp.helloworld",
    bundle_name = "Bitdrift Sample App",
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
    deps = [":ios_main"],
)

xcarchive(
    name = "ios_app_archive",
    bundle = ":ios_app_size",
    visibility = ["//visibility:public"],
)

ios_artifacts(
    name = "ios_app_simulator_artifact",
    srcs = [":ios_app.ipa"],
    cpu = "sim_arm64",
    tags = ["manual"],
)

sh_test(
    name = "ios_app_simulator_smoke_test",
    timeout = "long",
    srcs = ["//:ci/verify_ios_simulator_ipa.sh"],
    args = ["$(rootpath :ios_app_simulator_artifact)"],
    data = [":ios_app_simulator_artifact"],
    env = {
        "IOS_CI_SIMULATOR_RUNTIME": "com.apple.CoreSimulator.SimRuntime.iOS-26-2",
        "XCODE_VERSION_OVERRIDE": "26.0.1.17A400",
    },
    exec_compatible_with = ["@platforms//os:macos"],
    tags = ["manual"],
)

# rules_apple requires a profile label for a device app. The artifact embeds an
# empty placeholder, which the signing job replaces with its real profile.
genrule(
    name = "unsigned_size_profile",
    outs = ["unsigned-size.mobileprovision"],
    cmd = "touch $@",
)

# Keep this sample equivalent to ios_app_size so App Thinning can compare them.
ios_application(
    name = "ios_app_size_unsigned",
    app_icons = CI_APP_ICONS,
    bundle_id = "io.bitdrift.example.swiftapp.helloworld",
    bundle_name = "Bitdrift Sample App",
    executable_name = "hello_world_app",
    families = [
        "iphone",
        "ipad",
    ],
    features = ["disable_legacy_signing"],
    infoplists = ["Info.plist"],
    minimum_os_version = MINIMUM_IOS_VERSION,
    provisioning_profile = ":unsigned_size_profile",
    target_compatible_with = [
        "@platforms//os:ios",
        "@build_bazel_apple_support//constraints:device",
    ],
    version = ":hello_world_version",
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
    app_icons = CI_APP_ICONS,
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
