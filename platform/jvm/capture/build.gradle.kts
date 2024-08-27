plugins {
    // The rust android gradle plugin needs to go first
    //  see: https://github.com/mozilla/rust-android-gradle/issues/147
    alias(libs.plugins.rust.android)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)

    // Publish
    alias(libs.plugins.dokka) // Must be applied here for publish plugin.
    alias(libs.plugins.maven.publish)

    id("dependency-license-config")
}

group = "io.bitdrift"

dependencies {
    api(project(":replay"))
    api(libs.androidx.lifecycle.common)
    api(libs.androidx.lifecycle.process)
    api(libs.kotlin.result.jvm)
    api(libs.okhttp)

    implementation(project(":common"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.jsr305)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
}

android {
    namespace = "io.bitdrift.capture"

    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        ndkVersion = "27"
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        apiVersion = "1.9"
        jvmTarget = "1.8"
        allWarningsAsErrors = true
        languageVersion = "1.9"
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
    }

    ndkVersion = "27.0.12077973"
}

// Rust cargo build toolchain
cargo {
    libname = "capture"
    extraCargoBuildArguments = listOf("--package", "capture")
    module = "../.."
    targetDirectory = "../../../target"
    targets = listOf("arm64", "x86_64")
    pythonCommand = "python3"
}

// workaround bug in rust-android-gradle plugin that causes .so to not be available on app launch
// see: https://github.com/mozilla/rust-android-gradle/issues/118#issuecomment-1569407058
tasks.whenTaskAdded {
    if ((this.name == "mergeDebugJniLibFolders" || this.name == "mergeReleaseJniLibFolders")) {
        this.dependsOn("cargoBuild")
        this.inputs.dir(layout.buildDirectory.dir("rustJniLibs/android"))
    }
}

//detekt
detekt {
    // Define the detekt configuration(s) you want to use.
    // Defaults to the default detekt configuration.
    config.setFrom("detekt.yml")
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
    }
}
