plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        allWarningsAsErrors = true
    }

    lint {
        quiet = false
        ignoreWarnings = false
        abortOnError = true
        checkDependencies = true
    }

    namespace = "io.bitdrift.capture.common"
}
