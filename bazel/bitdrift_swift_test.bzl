load("@rules_apple//apple:ios.bzl", "ios_unit_test")
load("@rules_cc//cc:defs.bzl", "objc_library")
load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:config.bzl", "APPLE_TARGET_COMPATIBILITY", "MINIMUM_IOS_VERSION_TESTS")
load("//bazel/platforms:configured_test.bzl", "macos_test")

# Rust, Swift, ObjC, and C++ TSan instrumentation can exceed ld's compact-unwind
# personality limit in one XCTest bundle.
TSAN_LINKOPTS = select({
    "//bazel/ios:ios_tsan": ["-no_compact_unwind"],
    "//conditions:default": [],
})

# Macro providing a way to easily/consistently define Swift unit test targets.
#
# - Prevents consumers from having to define both swift_library and ios_unit_test targets
# - Provides a set of linker options that is required to properly run tests
# - Sets default visibility and OS requirements
#
def bitdrift_mobile_swift_test(name, srcs, data = [], deps = [], tags = [], use_test_host = False, repository = "", visibility = [], xcode = "", simulator = "", build_data = [], swift_features = []):
    test_lib_name = name + "_lib"
    wrapped = "macos_only" in tags
    xctest_name = name + "_xctest" if wrapped else name
    swift_library(
        name = test_lib_name,
        target_compatible_with = APPLE_TARGET_COMPATIBILITY,
        srcs = srcs,
        data = data,
        deps = deps,
        features = swift_features,
        linkopts = ["-lresolv.9"],
        testonly = True,
        visibility = ["//visibility:private"],
        tags = ["manual"],
    )

    test_host = None
    if use_test_host:
        test_host = "//test/platform/swift/test_host:TestHost"

    ios_unit_test(
        name = xctest_name,
        target_compatible_with = APPLE_TARGET_COMPATIBILITY,
        data = data + build_data,
        deps = [test_lib_name],
        linkopts = TSAN_LINKOPTS,
        minimum_os_version = MINIMUM_IOS_VERSION_TESTS,
        timeout = "long",
        tags = tags + [
            # CI reuses one global simulator; parallel xcodebuild test runs can
            # cause a runner to exit before it establishes its test connection.
            "exclusive",
        ] + (["manual"] if wrapped and "manual" not in tags else []),
        test_host = test_host,
        visibility = visibility,
    )

    if wrapped:
        macos_test(
            name = name,
            test = ":" + xctest_name,
            xcode = xcode,
            simulator = simulator,
            timeout = "long",
            xctest = True,
            tags = [tag for tag in tags if tag != "exclusive"] + ["exclusive-if-local"],
            visibility = visibility,
        )

def bitdrift_mobile_objc_test(name, srcs, data = [], deps = [], tags = [], visibility = []):
    test_lib_name = name + "_lib"
    objc_library(
        name = test_lib_name,
        target_compatible_with = APPLE_TARGET_COMPATIBILITY,
        srcs = srcs,
        data = data,
        deps = deps,
        visibility = ["//visibility:private"],
    )

    ios_unit_test(
        name = name,
        target_compatible_with = APPLE_TARGET_COMPATIBILITY,
        data = data,
        deps = [test_lib_name],
        linkopts = TSAN_LINKOPTS,
        minimum_os_version = MINIMUM_IOS_VERSION_TESTS,
        tags = tags,
        visibility = visibility,
    )
