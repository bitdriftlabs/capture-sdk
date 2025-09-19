import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.rust.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)

    // Publish
    alias(libs.plugins.dokka) // Must be applied here for publish plugin.
    alias(libs.plugins.maven.publish)

    id("dependency-license-config")
    id("com.google.protobuf") version "0.9.4"
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
    implementation(libs.protobuf.kotlinlite)

    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.kotlin.inline)
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

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
            apiVersion = KotlinVersion.KOTLIN_1_9
            languageVersion = KotlinVersion.KOTLIN_1_9
            allWarningsAsErrors = true
            freeCompilerArgs.addAll(listOf("-Xdont-warn-on-error-suppression")) // needed for suppressing INVISIBLE_REFERENCE etc
        }
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
        disable.add("AndroidGradlePluginVersion")
    }

}

// Rust cargo build toolchain
cargoNdk {
    librariesNames = arrayListOf("libcapture.so")
    extraCargoBuildArguments = arrayListOf("--package", "capture")

    buildTypes {
        getByName("release") {
            buildType = "release"
            extraCargoBuildArguments = arrayListOf(
                "--package", "capture",
                "-Z", "build-std=std,panic_abort",
                "-Z", "build-std-features=optimize_for_size,panic_immediate_abort",
            )
        }

        getByName("debug") {
            buildType = "dev"
        }
    }

    module = "../.."
    targetDirectory = "./target"
    // Default set for local dev on ARM-based macos
    targets = arrayListOf("arm64")
    // enable 16 KB ELF alignment on Android to support API 35+
    extraCargoEnv = mapOf(
      "RUSTFLAGS" to "-C link-args=-Wl,-z,max-page-size=16384,--build-id",
      "RUSTC_BOOTSTRAP" to "1" // Required for using unstable features in the Rust compiler
    )
}

// detekt
detekt {
    // Define the detekt configuration(s) you want to use.
    // Defaults to the default detekt configuration.
    config.setFrom("detekt.yml")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
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
