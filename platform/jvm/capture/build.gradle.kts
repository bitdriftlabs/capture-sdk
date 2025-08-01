plugins {
    alias(libs.plugins.android.library)

    // The rust android gradle plugin needs to go first
    //  see: https://github.com/mozilla/rust-android-gradle/issues/147
    alias(libs.plugins.rust.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)

    // Publish
    alias(libs.plugins.dokka) // Must be applied here for publish plugin.
    alias(libs.plugins.maven.publish)

    id("dependency-license-config")
    id("com.google.protobuf") version "0.9.1"
}

group = "io.bitdrift"

dependencies {
    api(project(":replay"))
    api(libs.androidx.lifecycle.common)
    api(libs.androidx.lifecycle.process)
    api(libs.kotlin.result.jvm)
    api(libs.okhttp)
    api(libs.flatbuffers)

    implementation(project(":common"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.jsr305)
    implementation(libs.gson)
    implementation(libs.performance)
    implementation(libs.protobuf.kotlinlite)

    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
}

android {
    namespace = "io.bitdrift.capture"

    compileSdk = 36

    defaultConfig {
        minSdk = 24
        ndkVersion = "27"
        consumerProguardFiles("consumer-rules.pro")
    }

    ndkVersion = "27.2.12479018"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // TODO(murki): consider updating to using kotlin.compilerOptions {} block
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "2.1"
        languageVersion = "2.1"
        allWarningsAsErrors = true
    }

    // TODO(murki): Move this common configuration to a reusable buildSrc plugin once it's fully supported for kotlin DSL
    //  see: https://github.com/gradle/kotlin-dsl-samples/issues/1287
    lint {
        quiet = false
        ignoreWarnings = false
        warningsAsErrors = true
        checkAllWarnings = true
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        disable.add("GradleDependency")
    }
}

// Rust cargo build toolchain
cargoNdk {
    librariesNames = arrayListOf("libcapture.so")
    extraCargoBuildArguments = arrayListOf("--package", "capture")
    module = "../.."
    targetDirectory = "./target"
    targets = arrayListOf("arm64", "x86_64")
    // enable 16 KB ELF alignment on Android to support API 35+
    extraCargoEnv = mapOf("RUST_ANDROID_GRADLE_CC_LINK_ARG" to "-Wl,-z,max-page-size=16384")
}

// detekt
detekt {
    // Define the detekt configuration(s) you want to use.
    // Defaults to the default detekt configuration.
    config.setFrom("detekt.yml")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.2"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}


tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude {
        it.file.absolutePath.contains("test/")
    }
}

tasks.preBuild {
    dependsOn("detekt")
}

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repos/releases"))
        }
        mavenLocal()
    }
}
