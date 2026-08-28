load("@rules_apple//apple:apple.bzl", "apple_static_framework_import")
load("@rules_kotlin//kotlin:core.bzl", "define_kt_toolchain", "kt_compiler_plugin", "kt_kotlinc_options")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
load("@rules_multirun//:defs.bzl", "multirun")
load("@rules_pkg//:pkg.bzl", "pkg_zip")
load("@rules_shell//shell:sh_test.bzl", "sh_test")
load(
    "@rules_xcodeproj//xcodeproj:defs.bzl",
    "top_level_targets",
    "xcodeproj",
    "xcschemes",
)
load("//bazel:android_debug_info.bzl", "android_debug_info")
load("//bazel:framework_imports_extractor.bzl", "framework_imports_extractor")
load("//bazel:rustfmt.bzl", "rustfmt_runner")
load("//bazel/android:artifacts.bzl", "android_artifacts")
load("//bazel/ios:xcframework.bzl", "strip_rust_metadata_xcframework")

alias(
    name = "ios_app",
    actual = "//examples/swift/hello_world:ios_app",
)

alias(
    name = "android_app",
    actual = "//examples/android:android_app",
)

multirun(
    name = "ktlint_fix_all",
    commands = [
        "//bazel/android:_test_suite_lib_ktlint_fix",
        "//platform/jvm/capture:_capture_logger_lib_ktlint_fix",
        "//platform/jvm/capture:_test_ktlint_fix",
        "//platform/jvm/common:_lib_ktlint_fix",
        "//platform/jvm/replay:_lib_ktlint_fix",
        "//platform/jvm/replay:_test_ktlint_fix",
    ],
    jobs = 0,
)

rustfmt_runner(
    name = "rustfmt",
)

strip_rust_metadata_xcframework(
    name = "ios_xcframework_for_distribution",
    metadata_stripper = "//bazel/ios:strip_rust_metadata",
    visibility = ["//visibility:public"],
    xcframework = "//platform/swift/source:Capture",
)

sh_test(
    name = "ios_xcframework_archive_metadata_test",
    srcs = ["ci/check_ios_xcframework_archive_members.sh"],
    data = [":ios_xcframework_for_distribution"],
    tags = ["macos_only"],
)

pkg_zip(
    name = "ios_dist",
    srcs = [
        ":ios_xcframework_for_distribution",
        ":license",
    ],
    out = "Capture.ios.zip",
    tags = ["local"],
    visibility = ["//visibility:public"],
)

pkg_zip(
    name = "ios_doccarchive",
    srcs = ["//platform/swift/source:Capture.doccarchive"],
    out = "Capture.doccarchive.ios.zip",
    tags = ["local"],
    visibility = ["//visibility:public"],
)

# Ideally it should live inside of platform/swift/source directory
# but its implementation depends on it being located in a root directory.
apple_static_framework_import(
    name = "capture_apple_static_framework_import",
    framework_imports = [":capture_ios_framework_imports"],
    sdk_dylibs = [
        "resolv.9",
        "c++",
    ],
    sdk_frameworks = [
        "Network",
        "SystemConfiguration",
        "UIKit",
    ],
    visibility = ["//visibility:public"],
)

framework_imports_extractor(
    name = "capture_ios_framework_imports",
    framework = "//platform/swift/source:capture_ios_static_framework",
)

filegroup(
    name = "license",
    srcs = [
        "ci/LICENSE",
    ],
)

android_artifacts(
    name = "capture_aar",
    android_library = "//platform/jvm/capture:capture_logger_lib",
    archive_name = "capture",
    manifest = "//platform/jvm:AndroidManifest.xml",
    native_deps = select({
        # When targeting an optimized build, use the stripped binary. The symbols are collected prior to stripping and exposed via capture_symbols below.
        "//bazel/android:strip_symbols": [":capture.debug_info"],
        "//conditions:default": ["//platform/jvm:capture_shared"],
    }),
    proguard_rules = "//platform/jvm:proguard",
    sdk_verification_file = select({
        "//bazel/android:android_sdk_verification_file": ["//platform/jvm/capture:sdk_verification_file"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)

# Measures the compressed x86_64 native library as it is packaged for Android.
# capture_aar uses android_debug_info under the release configuration, so the
# library in the AAR has already been stripped by Bazel's NDK toolchain.
genrule(
    name = "capture_aar_so_size_x86_64",
    srcs = [":capture_aar"],
    outs = ["capture_aar_so_size_x86_64_kb.txt"],
    cmd = """
set -euo pipefail

work_dir="$(@D)/capture_aar_so_size_x86_64"
mkdir -p "$$work_dir"
zipper="$(location @bazel_tools//tools/zip:zipper)"

"$$zipper" x "$(location :capture_aar)" -d "$$work_dir" jni/x86_64/libcapture.so
"$$zipper" cC "$$work_dir/libcapture.so.zip" \
    "jni/x86_64/libcapture.so=$$work_dir/jni/x86_64/libcapture.so"

size_bytes=$$(wc -c < "$$work_dir/libcapture.so.zip")
echo $$((($$size_bytes + 1023) / 1024)) > "$@"
""",
    tools = ["@bazel_tools//tools/zip:zipper"],
    visibility = ["//visibility:public"],
)

android_debug_info(
    name = "capture.debug_info",
    dep = "//platform/jvm:capture_shared",
    tags = ["manual"],
)

# Combines all the symbols outputted by the above aar into a single symbols.tar file.
genrule(
    name = "capture_symbols",
    srcs = [
        ":capture_aar_symbols_collector",
    ],
    outs = ["symbols.tar"],
    cmd = """
    out="$$(pwd)/$(OUTS)"

    mkdir -p tmp/
    for artifact in "$(SRCS)"; do
      cp $$artifact ./tmp/
    done

    cd tmp/
    tar cvf "$$out" *
    """,
    tools = ["//bazel:zipper"],
)

exports_files([
    "rustfmt.toml",
    ".clippy.toml",
])

kt_kotlinc_options(
    name = "kt_kotlinc_options",
    warn = "error",
    # Kotlin 2.3 warns when compiling the SDK's supported 1.9 language level; each Gradle module
    # suppresses the same warning in its compilerOptions.
    x_suppress_version_warnings = True,
)

kt_javac_options(
    name = "kt_javac_options",
)

KOTLIN_LANG_VERSION = "1.9"

JAVA_LANG_VERSION = "1.8"

define_kt_toolchain(
    name = "kotlin_toolchain",
    api_version = KOTLIN_LANG_VERSION,
    experimental_use_abi_jars = True,
    javac_options = "//:kt_javac_options",
    jvm_target = JAVA_LANG_VERSION,
    kotlinc_options = "//:kt_kotlinc_options",
    language_version = KOTLIN_LANG_VERSION,
)

# Define the compose compiler plugin
# Used by referencing //:jetpack_compose_compiler_plugin
kt_compiler_plugin(
    name = "jetpack_compose_compiler_plugin",
    id = "androidx.compose.compiler",
    target_embedded_compiler = True,
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org_jetbrains_kotlin_kotlin_compose_compiler_plugin_embeddable",
    ],
)

xcodeproj(
    name = "xcodeproj",
    bazel_path = "./bazelw",
    default_xcode_configuration = "Debug",
    project_name = "Capture",
    tags = ["manual"],
    top_level_targets = [
        # Apps
        top_level_targets(
            labels = [
                "//examples/swift/hello_world:hello_world_app",
                "//examples/objective-c:hello_world_app",
                "//examples/swift/session_replay_preview:session_replay_preview_app",
                "//examples/swift/benchmark:benchmark_app",
                # Tests
                # Running benchmark tests doesn't work on real devices
                # See https://github.com/MobileNativeFoundation/rules_xcodeproj/issues/2395
                # for more details.
                "//test/platform/swift/benchmark:run_benchmarks",
            ],
            target_environments = [
                "device",
                "simulator",
            ],
        ),
        # Tests
        "//test/platform/swift/unit_integration/core:test",
        "//test/platform/swift/unit_integration/integrations:test",
    ],
    xcode_configurations = {
        "Debug": {
            "//command_line_option:compilation_mode": "dbg",
            "//command_line_option:features": [],
        },
        "Release": {
            "//command_line_option:compilation_mode": "opt",
            "//command_line_option:features": ["swift.enable_testing"],
        },
    },
    xcschemes = [
        xcschemes.scheme(
            name = "iOS Hello World App",
            run = xcschemes.run(
                env = {
                    "RUST_LOG": "info,bd_crash_reporter=debug,swift_bridge=debug",
                },
                launch_target = xcschemes.launch_target("//examples/swift/hello_world:hello_world_app"),
            ),
        ),
        xcschemes.scheme(
            name = "iOS Hello World (ObjC)",
            run = xcschemes.run(
                launch_target = xcschemes.launch_target("//examples/objective-c:hello_world_app"),
            ),
        ),
        xcschemes.scheme(
            name = "iOS Session Replay App",
            run = xcschemes.run(
                launch_target = xcschemes.launch_target("//examples/swift/session_replay_preview:session_replay_preview_app"),
            ),
        ),
        xcschemes.scheme(
            name = "iOS Benchmark App",
            run = xcschemes.run(
                launch_target = xcschemes.launch_target("//examples/swift/benchmark:benchmark_app"),
            ),
        ),
        xcschemes.scheme(
            name = "iOS Capture Unit Integration Tests",
            test = xcschemes.test(
                test_targets = [
                    "//test/platform/swift/unit_integration/core:test",
                ],
            ),
        ),
        xcschemes.scheme(
            name = "iOS Capture URLSession Integration Tests",
            test = xcschemes.test(
                test_targets = [
                    "//test/platform/swift/unit_integration/integrations:test",
                ],
            ),
        ),
        xcschemes.scheme(
            name = "iOS Benchmark Tests",
            test = xcschemes.test(
                test_targets = [
                    "//test/platform/swift/benchmark:run_benchmarks",
                ],
            ),
        ),
    ],
)
