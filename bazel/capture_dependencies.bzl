load("@rules_jvm_external//:defs.bzl", "maven_install")

def jvm_dependencies():
    okhttp_version = "4.12.0"
    lifecycle_version = "2.6.1"
    compose_version = "1.4.0"
    kotlin_compile_version = "1.9.24"

    maven_install(
        artifacts = [
            "com.google.code.findbugs:jsr305:3.0.2",
            # Dokka (javadocs generator)
            "org.jetbrains.dokka:analysis-kotlin-descriptors:1.9.10",
            "org.jetbrains.dokka:dokka-base:1.9.10",
            "org.jetbrains.dokka:dokka-cli:1.9.10",
            # Test artifacts
            "org.mockito:mockito-core:4.11.0",
            "org.mockito:mockito-inline:4.11.0",
            "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0",
            "androidx.test:core:1.6.0",
            "org.robolectric:robolectric:4.13",
            "org.assertj:assertj-core:3.22.0",
            "com.squareup.okhttp3:mockwebserver:{}".format(okhttp_version),
            "junit:junit:4.13.2",
            "io.github.classgraph:classgraph:4.8.146",
            # Library dependencies
            "com.michael-bull.kotlin-result:kotlin-result-jvm:1.1.18",
            "com.google.code.gson:gson:2.10.1",
            "com.squareup.okhttp3:okhttp:{}".format(okhttp_version),
            "androidx.startup:startup-runtime:1.1.1",
            "androidx.core:core:1.9.0",
            "net.bytebuddy:byte-buddy:1.12.19",
            "com.google.guava:listenablefuture:1.0",  #required by androidx.lifecycle:lifecycle-process below
            "androidx.lifecycle:lifecycle-common:{}".format(lifecycle_version),
            "androidx.lifecycle:lifecycle-process:{}".format(lifecycle_version),
            "org.jetbrains.kotlin:kotlin-stdlib:{}".format(kotlin_compile_version),
            "androidx.appcompat:appcompat:1.5.1",
            "androidx.activity:activity-compose:1.8.0",
            "androidx.compose.material:material:{}".format(compose_version),
            "androidx.compose.ui:ui:{}".format(compose_version),
            "androidx.compose.compiler:compiler:1.5.14",
            "androidx.emoji2:emoji2:1.2.0",
        ],
        version_conflict_policy = "pinned",
        repositories = [
            "https://repo1.maven.org/maven2",
            "https://maven.google.com",
        ],
    )
