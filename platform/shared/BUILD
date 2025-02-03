load("@rules_rust//cargo:defs.bzl", "cargo_build_script")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_library")

# This shared library is consumed by the jvm targets which produce a file located
# at src/platform/jvm named either libcapture.so or libcapture.dylib depending on the platform.
# This allows the JniLibrary to load this with the name "capture" when the library path is set
# to src/platform/jvm.
cc_binary(
    name = "capture",
    linkopts = [
        "-lm",  # Required to avoid dlopen runtime failures unrelated to rust
        "-lz",  # Link against system zlib library
    ] + select({
        "@platforms//os:android": [
            "-Wl,-z,max-page-size=16384",  # enable 16 KB ELF alignment on Android to support API 35+
            "-Wl,--retain-symbols-file=$(location :jni_symbols.lds)",
        ],
        "@platforms//os:linux": ["-Wl,--retain-symbols-file=$(location :jni_symbols.lds)"],
        # The linker on macos doesn't support the same options or file formats as linux, so use a pattern match here (not availabile on linux).
        # We could use another file which prefixes all the symbols with _, but this seems easier.
        "@platforms//os:macos": [
            "-Wl,-exported_symbol,_Java_io_bitdrift_capture_network*",
            "-Wl,-exported_symbol,_Java_io_bitdrift_capture_CaptureJni*",
            "-Wl,-exported_symbol,_Java_io_bitdrift_capture_Jni*",
            "-Wl,-exported_symbol,_JNI_OnLoad",
            "-Wl,-framework,Security",
        ],
        "//conditions:default": ["@platforms//:incompatible"],
    }) + select({
        "//bazel:dbg_build": ["-Wl,--build-id"],
        "//conditions:default": [],
    }),
    linkshared = True,
    tags = [
        "no-cache",
        "no-remote",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "jni_symbols.lds",
        ":capture_jni",
    ],
)

bitdrift_rust_library(
    name = "platform-shared",
    srcs = glob(["src/**/*.rs"]),
    visibility = ["//visibility:public"],
    deps = [":build_script"],
)

cargo_build_script(
    name = "build_script",
    srcs = ["build.rs"],
    data = [":sdk_version"],
    edition = "2021",
)

filegroup(
    name = "sdk_version",
    srcs = [".sdk_version"],
    visibility = ["//visibility:public"],
)
