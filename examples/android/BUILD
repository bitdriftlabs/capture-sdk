load("@rules_android//android:rules.bzl", "aar_import", "android_binary")
load("@rules_jvm_external//:defs.bzl", "artifact")
load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")

genrule(
    name = "generate_capture_sdk_config",
    outs = ["CaptureSdkConfig.kt"],
    cmd = """
set -euo pipefail

# Escape for Kotlin string literals
escape() {
  printf '%s' "$$1" | sed -e 's/\\\\/\\\\\\\\/g' -e 's/\"/\\\"/g'
}

API_KEY_ESC=$$(escape "$${CAPTURE_SDK_API_KEY:-}")
API_URL_ESC=$$(escape "$${CAPTURE_SDK_API_URL:-https://api.bitdrift.io}")

cat > $@ <<EOF
package io.bitdrift.capture.helloworld

object CaptureSdkConfig {
    const val API_KEY: String = "$$API_KEY_ESC"
    const val API_URL: String = "$$API_URL_ESC"
}
EOF
""",
)

_maven_deps = [
    artifact("com.google.code.findbugs:jsr305"),
    artifact("com.squareup.okhttp3:okhttp"),
    artifact("androidx.core:core"),
    artifact("androidx.startup:startup-runtime"),
    artifact("androidx.lifecycle:lifecycle-common"),
    artifact("androidx.lifecycle:lifecycle-common-jvm"),
    artifact("androidx.lifecycle:lifecycle-process"),
    artifact("androidx.appcompat:appcompat"),
    artifact("androidx.activity:activity-compose"),
    artifact("androidx.compose.material:material"),
    artifact("com.google.code.gson:gson"),
    artifact("com.google.flatbuffers:flatbuffers-java"),
    artifact("com.google.protobuf:protobuf-javalite"),
    artifact("com.google.protobuf:protobuf-kotlin-lite"),
    artifact("androidx.annotation:annotation-jvm"),
    artifact("androidx.compose.runtime:runtime_android"),
    artifact("androidx.compose.ui:ui_android"),
    artifact("androidx.compose.ui:ui-geometry_android"),
    artifact("androidx.compose.ui:ui-unit_android"),  # Why do I need to prepend android to these?
    artifact("com.squareup.okio:okio"),
    artifact("com.squareup.okio:okio-jvm"),
    artifact("org.jetbrains.kotlin:kotlin-stdlib"),
    artifact("org.jetbrains:annotations"),
    artifact("androidx.metrics:metrics-performance"),
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
        ":generate_capture_sdk_config",
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
