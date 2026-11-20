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
def bitdrift_mobile_swift_test(name, srcs, data = [], deps = [], tags = [], use_test_host = False, repository = "", visibility = [], xcode = "", simulator = "", build_data = [], target_compatible_with = []):
    test_lib_name = name + "_lib"
    xctest_name = name + "_xctest"
    tags = [tag for tag in tags if tag not in ["macos_only", "exclusive"]] + ["macos_only"]
    swift_library(
        name = test_lib_name,
        target_compatible_with = APPLE_TARGET_COMPATIBILITY,
        srcs = srcs,
        data = data,
        deps = deps,
        linkopts = ["-lresolv.9"],
        testonly = True,
        visibility = ["//visibility:private"],
        tags = ["manual"],
    )

    test_host = None
    if use_test_host:
        test_host = "//test/platform/swift/test_host:TestHost"

    # Only the public test below should be discovered by bazel test //....
    ios_unit_test(
        name = xctest_name,
        target_compatible_with = APPLE_TARGET_COMPATIBILITY,
        data = data + build_data,
        deps = [test_lib_name],
        linkopts = TSAN_LINKOPTS,
        minimum_os_version = MINIMUM_IOS_VERSION_TESTS,
        timeout = "long",
        tags = [tag for tag in tags if tag != "manual"] + ["exclusive", "manual"],
        test_host = test_host,
        visibility = visibility,
    )

    macos_test(
        name = name,
        test = ":" + xctest_name,
        xcode = xcode,
        simulator = simulator,
        timeout = "long",
        xctest = True,
        tags = tags + ["exclusive-if-local"],
        target_compatible_with = target_compatible_with,
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
