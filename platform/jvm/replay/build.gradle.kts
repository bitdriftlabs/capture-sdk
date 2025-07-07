plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

    id("dependency-license-config")

    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.ui)
    implementation(libs.okhttp)

    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit)
}

group = "io.bitdrift"

android {
    namespace = "io.bitdrift.capture.replay"

    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        allWarningsAsErrors = true
        // Needed to be able to use INVISIBLE_REFERENCE see https://youtrack.jetbrains.com/issue/KT-67920
        freeCompilerArgs += listOf("-Xdont-warn-on-error-suppression")
    }

    lint {
        quiet = false
        ignoreWarnings = false
        warningsAsErrors = true
        checkAllWarnings = true
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
    }
}

publishing {
    repositories {
      mavenLocal()
    }
}
