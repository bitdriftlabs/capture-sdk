load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def tool_dependencies():
    http_archive(
        name = "rules_multirun",
        sha256 = "9ced12fb88f793c2f0a8c19f498485c4a95c22c91bb51fc4ec6812d41fc3331d",
        strip_prefix = "rules_multirun-0.6.0",
        url = "https://github.com/keith/rules_multirun/archive/refs/tags/0.6.0.tar.gz",
    )

    http_archive(
        name = "DrString",
        build_file_content = """exports_files(["drstring"])""",
        sha256 = "860788450cf9900613454a51276366ea324d5bfe71d1844106e9c1f1d7dfd82b",
        url = "https://github.com/dduan/DrString/releases/download/0.5.2/drstring-x86_64-apple-darwin.tar.gz",
    )

    http_archive(
        name = "DrString_Linux",
        build_file_content = """
load("@bazel_skylib//rules:native_binary.bzl", "native_binary")

native_binary(
    name = "DrString_Linux",
    src = "usr/bin/drstring",
    out = "usr/bin/drstring",
    data = glob(["usr/lib/*.so"]),
    visibility = ["//visibility:public"],
)
        """,
        sha256 = "4589cfa00cebb31882ef4e51b2738e9974a51dc037b6a491ed25b0a120a415be",
        url = "https://github.com/dduan/DrString/releases/download/0.5.2/drstring-x86_64-unknown-ubuntu.tar.gz",
    )

    http_archive(
        name = "SwiftLint",
        sha256 = "75839dc9e8a492a86bb585a3cda3d73b58997d7a14d02f1dba94171766bb8599",
        url = "https://github.com/realm/SwiftLint/releases/download/0.53.0/bazel.tar.gz",
    )

    http_archive(
        name = "SwiftFormat",
        build_file_content = """
load("@build_bazel_rules_swift//swift:swift.bzl", "swift_binary", "swift_library")

swift_library(
    name = "lib",
    srcs = glob(["Sources/**/*.swift"]),
    copts = ["-DSWIFT_PACKAGE"],
    module_name = "SwiftFormat",
)

swift_binary(
    name = "swiftformat",
    srcs = glob(["CommandLineTool/**/*.swift"]),
    copts = ["-DSWIFT_PACKAGE"],
    module_name = "CommandLineTool",
    deps = [":lib"],
    visibility = ["//visibility:public"],
)
        """,
        sha256 = "f831e8be2524de2b47cb5ddf059573d9813625bb172de123e5a106d9f4d2f7ea",
        url = "https://github.com/nicklockwood/SwiftFormat/archive/refs/tags/0.52.9.tar.gz",
        strip_prefix = "SwiftFormat-0.52.9",
    )
