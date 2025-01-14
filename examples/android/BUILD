load("@rules_android//android:rules.bzl", "aar_import", "android_binary")
load("@rules_jvm_external//:defs.bzl", "artifact")
load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")

_maven_deps = [
    artifact("com.google.code.findbugs:jsr305"),
    artifact("com.squareup.okhttp3:okhttp"),
    artifact("androidx.core:core"),
    artifact("androidx.startup:startup-runtime"),
    artifact("androidx.lifecycle:lifecycle-common"),
    artifact("androidx.lifecycle:lifecycle-process"),
    artifact("androidx.appcompat:appcompat"),
    artifact("androidx.activity:activity-compose"),
    artifact("androidx.compose.material:material"),
    artifact("com.michael-bull.kotlin-result:kotlin-result-jvm"),
    artifact("com.google.code.gson:gson"),
    artifact("androidx.annotation:annotation-jvm"),
    artifact("androidx.compose.ui:ui"),
    artifact("androidx.compose.ui:ui-geometry"),
    artifact("androidx.compose.ui:ui-unit"),
    artifact("androidx.compose.runtime:runtime"),
    artifact("com.squareup.okio:okio"),
    artifact("com.squareup.okio:okio-jvm"),
    artifact("org.jetbrains.kotlin:kotlin-stdlib"),
    artifact("org.jetbrains:annotations"),
]

aar_import(
    name = "imported_capture_aar",
    aar = "//:capture_aar",
    deps = _maven_deps,
)

kt_android_library(
    name = "hello_world_kt",
    srcs = [
        "HelloWorldApp.kt",
        "MainActivity.kt",
    ],
    custom_package = "io.bitdrift.capture.helloworld",
    manifest = "AndroidManifest.xml",
    plugins = ["//:jetpack_compose_compiler_plugin"],
    resource_files = ["res/layout/android_main.xml"],
    deps = [":imported_capture_aar"] + _maven_deps,
)

android_binary(
    name = "android_app",
    custom_package = "io.bitdrift.capture.helloworld",
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "applicationId": "io.bitdrift.capture.helloworld",
    },
    proguard_specs = ["base.pro"],
    visibility = ["//visibility:public"],
    deps = [":hello_world_kt"],
)
