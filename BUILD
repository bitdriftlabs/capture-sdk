load("@rules_apple//apple:apple.bzl", "apple_static_framework_import")
load("@rules_java//java:defs.bzl", "java_binary")
load("@rules_kotlin//kotlin:core.bzl", "define_kt_toolchain", "kt_compiler_plugin", "kt_kotlinc_options")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
load("@rules_pkg//:pkg.bzl", "pkg_zip")
load(
    "@rules_xcodeproj//xcodeproj:defs.bzl",
    "top_level_targets",
    "xcodeproj",
    "xcschemes",
)
load("//bazel:android_debug_info.bzl", "android_debug_info")
load("//bazel:framework_imports_extractor.bzl", "framework_imports_extractor")
load("//bazel/android:artifacts.bzl", "android_artifacts")
load("//bazel/ios:hack.bzl", "rewrite_xcframework")

alias(
    name = "ios_app",
    actual = "//examples/swift/hello_world:ios_app",
)

alias(
    name = "android_app",
    actual = "//examples/android:android_app",
)

rewrite_xcframework(
    name = "ios_xcframework_with_rust_symbols",
    rewrite_tool = "//bazel/ios:rewrite_symbols",
    visibility = ["//visibility:public"],
    xcframework = "//platform/swift/source:Capture",
)

pkg_zip(
    name = "ios_dist",
    srcs = [
        ":ios_xcframework_with_rust_symbols",
        ":license",
    ],
    out = "Capture.ios.zip",
    tags = ["local"],
    visibility = ["//visibility:public"],
)

pkg_zip(
    name = "ios_doccarchive",
    srcs = ["//platform/swift/source:Capture.doccarchive" ],
    out = "Capture.ios.doccarchive.zip",
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
    tags = [
        "no-cache",
        "no-remote",
    ],
)

filegroup(
    name = "license",
    srcs = [
        "ci/LICENSE.txt",
        "ci/NOTICE.txt",
    ],
)

android_artifacts(
    name = "capture_aar",
    android_library = "//platform/jvm/capture:capture_logger_lib",
    archive_name = "capture",
    excluded_artifacts = [
        "com.google.code.findbugs:jsr305",
    ],
    manifest = "//platform/jvm:AndroidManifest.xml",
    native_deps = select({
        # When targeting an optimized build, use the stripped binary. The symbols are collected prior to stripping and exposed via capture_symbols below.
        "//bazel/android:strip_symbols": [":capture.debug_info"],
        "//conditions:default": ["//platform/jvm:capture_shared"],
    }),
    proguard_rules = "//platform/jvm:proguard",
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
    build_mode = "bazel",
    default_xcode_configuration = "Debug",
    generation_mode = "incremental",
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

java_binary(
    name = "bazel-diff",
    main_class = "com.bazel_diff.Main",
    runtime_deps = ["@bazel_diff//jar"],
)
