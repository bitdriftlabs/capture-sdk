load("@rules_apple//apple:ios.bzl", "ios_application")
load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION_TESTS")

# App that directly imports Bitdrift as a `swift_library` for compatibility with `rules_xcodeproj`
# and allowing building the app and all its deps in a single step.

swift_library(
    name = "ios_direct",
    testonly = True,
    srcs = glob(["*.swift"]),
    tags = ["manual"],
    deps = [
        "//platform/swift/source:ios_lib",
        "//test/platform/swift/benchmark:benchmarks",
        "//test/platform/swift/bridging:rust_bridge",
        "@SwiftBenchmark//:swift_benchmark",
    ],
)

ios_application(
    name = "benchmark_app",
    testonly = True,
    bundle_id = "io.bitdrift.example.benchmarkapp",
    families = ["iphone"],
    infoplists = ["Info.plist"],
    minimum_os_version = MINIMUM_IOS_VERSION_TESTS,
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
