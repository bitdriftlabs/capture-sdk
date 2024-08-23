load("@build_bazel_rules_apple//apple:ios.bzl", "ios_unit_test")
load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")
load("@rules_cc//cc:defs.bzl", "objc_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION_TESTS")

# Macro providing a way to easily/consistently define Swift unit test targets.
#
# - Prevents consumers from having to define both swift_library and ios_unit_test targets
# - Provides a set of linker options that is required to properly run tests
# - Sets default visibility and OS requirements
#
def bitdrift_mobile_swift_test(name, srcs, data = [], deps = [], tags = [], use_test_host = False, repository = "", visibility = []):
    test_lib_name = name + "_lib"
    swift_library(
        name = test_lib_name,
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

    ios_unit_test(
        name = name,
        data = data,
        deps = [test_lib_name],
        minimum_os_version = MINIMUM_IOS_VERSION_TESTS,
        timeout = "long",
        tags = tags + [
            "no-cache",
            "no-remote",
        ],
        test_host = test_host,
        visibility = visibility,
    )

def bitdrift_mobile_objc_test(name, srcs, data = [], deps = [], tags = [], visibility = []):
    test_lib_name = name + "_lib"
    objc_library(
        name = test_lib_name,
        srcs = srcs,
        data = data,
        deps = deps,
        visibility = ["//visibility:private"],
    )

    ios_unit_test(
        name = name,
        data = data,
        deps = [test_lib_name],
        minimum_os_version = MINIMUM_IOS_VERSION_TESTS,
        tags = tags,
        visibility = visibility,
    )
