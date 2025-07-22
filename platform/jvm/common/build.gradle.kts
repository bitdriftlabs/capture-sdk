plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

    id("dependency-license-config")
    alias(libs.plugins.maven.publish)
}

group = "io.bitdrift"

android {
    namespace = "io.bitdrift.capture.common"

    compileSdk = 35

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
        warningsAsErrors = true
        checkAllWarnings = true
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        disable.add("GradleDependency")
    }
}

publishing {
    repositories {
      mavenLocal()
    }
}
