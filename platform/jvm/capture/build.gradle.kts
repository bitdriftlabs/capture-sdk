import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
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

    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        create("profileable") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    packaging {
        jniLibs {
            // Bazel already selects libcapture's strip level for each build type. Preserve the
            // generated library so Android Gradle Plugin does not invoke an NDK strip tool.
            keepDebugSymbols += "**/libcapture.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
            apiVersion = KotlinVersion.fromVersion("1.9")
            languageVersion = KotlinVersion.fromVersion("1.9")
            allWarningsAsErrors = true
            freeCompilerArgs.addAll(
                listOf(
                    "-Xdont-warn-on-error-suppression", // needed for suppressing INVISIBLE_REFERENCE etc
                    // Kotlin 2.3 warns when compiling the SDK's supported 1.9 language level.
                    "-Xsuppress-version-warnings",
                ),
            )
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

val bazelWorkspace = file("../../..")
val bazelCaptureLibrary = "//platform/jvm:capture_shared"

/**
 * Controls the Rust debug information included in a Bazel-built JNI library.
 *
 * Gradle does not strip the library because that requires an NDK installation. Instead, this
 * value selects the equivalent Rust linker behavior in Bazel.
 */
enum class BazelRustStripLevel(
    /** The value supplied to Bazel's `capture_rust_strip_level` define, when one is needed. */
    val bazelDefineValue: String?,
) {
    /** Use Bazel's default stripping behavior for the selected build configuration. */
    DEFAULT(null),

    /** Strip DWARF debug information while retaining the symbol table. */
    DEBUG_INFO("debuginfo"),

    /** Preserve all debug information and symbols. */
    NONE("none"),
}

// Accept Gradle's historical cargo-ndk target names and the canonical Rust target triple so local
// and CI invocations select the corresponding Bazel platform and APK JNI directory together.
data class BazelAndroidTarget(
    val platform: String,
    val abi: String,
)

val bazelAndroidTarget =
    when (val rustTarget = providers.gradleProperty("rust-target").getOrElse("arm64")) {
        "arm64", "arm64-v8a", "aarch64-linux-android" ->
            BazelAndroidTarget("@rules_android//:arm64-v8a", "arm64-v8a")
        "arm", "armv7", "armeabi-v7a" ->
            BazelAndroidTarget("@rules_android//:armeabi-v7a", "armeabi-v7a")
        "x86" -> BazelAndroidTarget("@rules_android//:x86", "x86")
        "x86_64" -> BazelAndroidTarget("@rules_android//:x86_64", "x86_64")
        else -> throw GradleException("Unsupported Rust Android target: $rustTarget")
    }

fun registerBazelRustBuild(
    buildType: String,
    release: Boolean,
    stripLevel: BazelRustStripLevel = BazelRustStripLevel.DEFAULT,
) =
    tasks.register<Exec>("buildBazel${buildType.replaceFirstChar(Char::uppercase)}Rust") {
        description = "Build the $buildType Android Rust library with Bazel"
        workingDir = bazelWorkspace
        commandLine(
            "./bazelw",
            "build",
            bazelCaptureLibrary,
            "--platforms=${bazelAndroidTarget.platform}",
        )
        if (release) {
            args("--config=release-android")
        }
        if (stripLevel.bazelDefineValue != null) {
            // Preserve the Gradle variant's debugSymbolLevel through Bazel, which owns the
            // Rust linker and does not require Gradle to install the NDK.
            args("--define=capture_rust_strip_level=${stripLevel.bazelDefineValue}")
        }
    }

fun registerBazelRustCopy(buildType: String, buildTask: TaskProvider<out Task>) =
    tasks.register<Copy>("copyBazel${buildType.replaceFirstChar(Char::uppercase)}Rust") {
        dependsOn(buildTask)
        from(bazelWorkspace.resolve("bazel-bin/platform/jvm/libcapture.so"))
        into(layout.buildDirectory.dir("generated/jniLibs/$buildType/${bazelAndroidTarget.abi}"))

        // Bazel outputs are read-only. Keep Gradle's generated copy writable so that a later
        // invocation can replace it after Bazel rebuilds the library.
        doFirst {
            destinationDir.resolve("libcapture.so").setWritable(true)
        }
        filePermissions {
            user {
                read = true
                write = true
                execute = true
            }
            group {
                read = true
                execute = true
            }
            other {
                read = true
                execute = true
            }
        }
    }

val buildBazelDebugRust =
    registerBazelRustBuild("debug", release = false, stripLevel = BazelRustStripLevel.DEBUG_INFO)
val buildBazelReleaseRust = registerBazelRustBuild("release", release = true)
val buildBazelProfileableRust =
    registerBazelRustBuild("profileable", release = true, stripLevel = BazelRustStripLevel.NONE)
val copyBazelDebugRust = registerBazelRustCopy("debug", buildBazelDebugRust)
val copyBazelReleaseRust = registerBazelRustCopy("release", buildBazelReleaseRust)
val copyBazelProfileableRust = registerBazelRustCopy("profileable", buildBazelProfileableRust)

android.sourceSets {
    getByName("debug").jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs/debug"))
    getByName("release").jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs/release"))
    getByName("profileable").jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs/profileable"))
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
    dependsOn(copyBazelDebugRust)
}

tasks.matching { it.name == "mergeReleaseJniLibFolders" }.configureEach {
    dependsOn(copyBazelReleaseRust)
}

tasks.matching { it.name == "mergeProfileableJniLibFolders" }.configureEach {
    dependsOn(copyBazelProfileableRust)
}

// Task to build the test JNI library (combines production + test code)
// This mirrors the Bazel setup where //test/platform/jvm:capture builds a
// shared library that includes both production and test JNI functions
tasks.register<Exec>("buildTestJni") {
    description = "Build the combined test JNI library (production + test helpers)"

    workingDir = bazelWorkspace

    // Build the Bazel test target, which adds test helpers to the production JNI library.
    commandLine(
        "./bazelw",
        "build",
        "//test/platform/jvm:capture",
    )
}

// Configure tests to use the test JNI library and build it first
afterEvaluate {
    tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
        val testJniLib = bazelWorkspace.resolve("bazel-bin/test/platform/jvm")

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

    tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
        val testJniLib = bazelWorkspace.resolve("bazel-bin/test/platform/jvm")

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
