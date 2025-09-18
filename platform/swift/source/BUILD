load("@rules_apple//apple:apple.bzl", "apple_static_xcframework")
load("@rules_apple//apple:docc.bzl", "docc_archive")
load("@rules_apple//apple:ios.bzl", "ios_static_framework")
load("@rules_cc//cc:defs.bzl", "objc_library")
load("@rules_swift//swift:swift.bzl", "swift_library")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")
load("//bazel:config.bzl", "MINIMUM_IOS_VERSION")
load("//bazel:swift_header_collector.bzl", "swift_header_collector")

swift_library(
    name = "ios_lib",
    package_name = "BitdriftCapture",
    # Tell the linker that it should add "-lz" when linking the library.
    # See also https://www.smileykeith.com/2022/02/23/lc-linker-option/.
    # Passing "-lz" via `linkopts` doesn't work as we produce static library and `linkopts` is not read.
    # Bazel expects to apply `linkopts` when working with dynamic libraries instead.
    srcs = glob(
        include = ["**/*.swift"],
        exclude = ["bridging/**/*.swift"],
    ) + ["//proto:log_type_swift_source"],
    copts = [
        "-Xcc",
        "-Xclang",
        "-Xcc",
        "--linker-option=-lz",
    ] + select({
        "//bazel:is_ci": ["-warnings-as-errors"],
        "//conditions:default": [],
    }),
    data = [
        "//platform/swift/resources:maybe_info_plist",
        "//platform/swift/resources:privacy_manifest",
    ],
    features = [
        "swift.enable_library_evolution",
    ],
    generates_header = True,
    module_name = "Capture",
    private_deps = [
        ":CapturePassable",
        ":objc_bridge",
    ],
    tags = [
        "macos_only",
        "manual",
    ],
    visibility = ["//visibility:public"],
)

swift_library(
    name = "CapturePassable",
    srcs = glob(["bridging/**/*.swift"]),
    copts = select({
        "//bazel:is_ci": ["-warnings-as-errors"],
        "//conditions:default": [],
    }),
    features = [
        "swift.enable_library_evolution",
    ],
    generates_header = True,
    module_name = "CapturePassable",
    tags = [
        "macos_only",
        "manual",
    ],
    visibility = ["//visibility:public"],
)

bitdrift_rust_library(
    name = "swift_bridge",
    tags = [
        "macos_only",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//platform/shared:platform-shared",
    ],
)

objc_library(
    name = "objc_bridge",
    srcs = [
        "InternalAPI.m",
        "ObjCWrapper.mm",
        "reports/BitdriftKSCrashWrapper.m",
        "reports/DiagnosticEventReporter.m",
    ],
    hdrs = [
        "CaptureRustBridge.h",
        "InternalAPI.h",
        "ObjCWrapper.h",
        "reports/BitdriftKSCrashWrapper.h",
        "reports/DiagnosticEventReporter.h",
        "reports/bd-report-writer/ffi.h",
    ],
    module_name = "CaptureLoggerBridge",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        ":CapturePassable",
        ":bitdrift_enhanced_crash_data",
        ":swift_bridge",
    ],
)

objc_library(
    name = "bitdrift_enhanced_crash_data",
    srcs = [
        "crash_handler/BitdriftKSCrashHandler.m",
        "crash_handler/ReportContext.h",
        "crash_handler/ReportWriter.c",
        "crash_handler/ReportWriter.h",
        "crash_handler/bd-bonjson/ffi.h",
    ],
    hdrs = [
        "crash_handler/BitdriftKSCrashHandler.h",
    ],
    module_name = "BitdriftEnhancedCrashData",
    tags = ["macos_only"],
    visibility = ["//visibility:public"],
    deps = [
        ":CapturePassable",
        ":swift_bridge",
        "@kscrash//:kscrash_mini",
    ],
)

apple_static_xcframework(
    name = "Capture",
    ios = {
        "device": [
            "arm64",
        ],
        "simulator": [
            "arm64",
            "x86_64",
        ],
    },
    minimum_os_versions = {
        "ios": MINIMUM_IOS_VERSION,
    },
    tags = [
        "macos_only",
        "no-cache",
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":ios_lib",
    ],
)

swift_header_collector(
    name = "capture_ios_lib_headers",
    library = ":ios_lib",
    tags = ["manual"],
)

ios_static_framework(
    name = "capture_ios_static_framework",
    hdrs = ["capture_ios_lib_headers"],
    bundle_name = "Capture",
    minimum_os_version = MINIMUM_IOS_VERSION,
    tags = [
        "manual",
        "no-cache",
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [":ios_lib"],
)

docc_archive(
    name = "Capture.doccarchive",
    dep = ":capture_ios_static_framework",
    diagnostic_level = "information",
    enable_inherited_docs = True,
    fallback_bundle_identifier = "io.bitdrift.capture",
    fallback_bundle_version = "1.0.0",
    fallback_display_name = "Capture",
    tags = [
        "macos_only",
    ],
    transform_for_static_hosting = True,
    visibility = ["//visibility:public"],
)
