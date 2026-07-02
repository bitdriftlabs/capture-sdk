load("@rules_jvm_external//:defs.bzl", "artifact")
load("//bazel/android:build.bzl", "bitdrift_kt_jvm_library")

# Test suite used for all the jvm tests.
bitdrift_kt_jvm_library(
    name = "test_suite_lib",
    testonly = True,
    srcs = ["TestSuite.kt"],
    require_javadocs = False,
    visibility = ["//visibility:public"],
    deps = [
        artifact("io.github.classgraph:classgraph"),
        artifact("junit:junit"),
        artifact("org.assertj:assertj-core"),
    ],
)

config_setting(
    name = "strip_symbols",
    values = {"define": "android_strip_symbols=true"},
)

config_setting(
    name = "android_sdk_verification_file",
    values = {"define": "android_sdk_verification_file=true"},
)

exports_files(
    [
        "detekt.yml",
        "detekt_javadocs.yml",
    ],
)
