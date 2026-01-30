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
    implementation(libs.performance)
    implementation(libs.protobuf.kotlinlite)

    compileOnly(libs.retrofit)
    compileOnly(libs.androidx.webkit)

    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.webkit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.retrofit)
}

android {
    namespace = "io.bitdrift.capture"

    compileSdk = 36

    defaultConfig {
        minSdk = 23
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

// Rust cargo build toolchain for production library
cargoNdk {
    librariesNames = arrayListOf("libcapture.so")
    extraCargoBuildArguments = arrayListOf("--package", "capture")

    buildTypes {
        getByName("release") {
            buildType = "release"
            extraCargoBuildArguments =
                arrayListOf(
                    "--package",
                    "capture",
                    "-Z",
                    "build-std=std,panic_abort",
                    "-Z",
                    "build-std-features=optimize_for_size",
                )
            // Annoyingly we have to repeat all the flags for the release build as
            // the RUSTFLAGS values are not added together.
            // TODO(snowp): See if we can make this a bit better
            // enable 16 KB ELF alignment on Android to support API 35+
            extraCargoEnv =
                mapOf(
                    "RUSTFLAGS" to
                        "-Zunstable-options -Cpanic=immediate-abort -C link-args=-Wl,-z,max-page-size=16384,--build-id -C codegen-units=1 -C embed-bitcode -C lto=fat -C opt-level=z",
                    "RUSTC_BOOTSTRAP" to "1", // Required for using unstable features in the Rust compiler
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
    extraCargoEnv =
        mapOf(
            "RUSTFLAGS" to "-C link-args=-Wl,-z,max-page-size=16384,--build-id",
            "RUSTC_BOOTSTRAP" to "1", // Required for using unstable features in the Rust compiler
        )
}

// Detect the current platform and architecture for building the test JNI library
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

data class PlatformConfig(
    val rustTarget: String,
    val libExtension: String,
)

val platformConfig =
    when {
        osName.contains("mac") && osArch.contains("aarch64") -> PlatformConfig("aarch64-apple-darwin", "dylib")
        osName.contains("mac") -> PlatformConfig("x86_64-apple-darwin", "dylib")
        osName.contains("linux") && osArch.contains("aarch64") -> PlatformConfig("aarch64-unknown-linux-gnu", "so")
        osName.contains("linux") -> PlatformConfig("x86_64-unknown-linux-gnu", "so")
        else -> throw GradleException("Unsupported platform: $osName $osArch")
    }

// Task to build the test JNI library (combines production + test code)
// This mirrors the Bazel setup where //test/platform/jvm:capture builds a
// shared library that includes both production and test JNI functions
tasks.register<Exec>("buildTestJni") {
    description = "Build the combined test JNI library (production + test helpers)"

    workingDir = file("../../..")

    // Build the test_jni package which depends on capture-core (rlib) and adds test helpers
    commandLine(
        "cargo",
        "build",
        "--package",
        "test_jni",
        "--target",
        platformConfig.rustTarget,
    )

    // Clear RUSTFLAGS to use default flags for debug builds
    environment("RUSTFLAGS", "")

    // Output goes to target/${rustTarget}/debug/libcapture.${libExtension}
}

// Configure tests to use the test JNI library and build it first
afterEvaluate {
    tasks.named<Test>("testDebugUnitTest") {
        val testJniLib = file("../../../target/${platformConfig.rustTarget}/debug")

        // Set java.library.path to the test library location
        systemProperty("java.library.path", testJniLib.absolutePath)

        // Build test library before running tests
        dependsOn("buildTestJni")

        // Run tests in parallel with multiple forks, but still fork per test class
        // to avoid classloader issues with native library loading
        maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

        // Fork a new JVM for each test class to avoid native library classloader issues
        // This matches Bazel's approach where each test runs in isolation
        forkEvery = 1

        // Allow reflective access to java.base module for tests that need to manipulate
        // private fields (e.g., exception cycle tests)
        jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )

        // Exclude BuildConstantsTest - this test validates Bazel's build-time code generation
        // which doesn't apply to Gradle builds. Bazel generates BuildConstants.kt with the actual
        // SDK version from .sdk_version, while Gradle uses a stub file with "x.x.x".
        exclude("**/BuildConstantsTest.class")

        // Exclude logger_client_metadata test - needs more debugging.
        filter {
            excludeTestsMatching("io.bitdrift.capture.CaptureLoggerTest.logger_client_metadata")
        }
    }

    tasks.named<Test>("testReleaseUnitTest") {
        val testJniLib = file("../../../target/${platformConfig.rustTarget}/debug")

        // Set java.library.path to the test library location
        systemProperty("java.library.path", testJniLib.absolutePath)

        // Build test library before running tests
        dependsOn("buildTestJni")

        // Run tests in parallel with multiple forks, but still fork per test class
        // to avoid classloader issues with native library loading
        maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

        // Fork a new JVM for each test class
        forkEvery = 1

        // Allow reflective access
        jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )

        // Exclude BuildConstantsTest - this test validates Bazel's build-time code generation
        // which doesn't apply to Gradle builds. Bazel generates BuildConstants.kt with the actual
        // SDK version from .sdk_version, while Gradle uses a stub file with "x.x.x".
        exclude("**/BuildConstantsTest.class")

        // Exclude logger_client_metadata test - needs more debugging.
        filter {
            excludeTestsMatching("io.bitdrift.capture.CaptureLoggerTest.logger_client_metadata")
        }
    }
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
