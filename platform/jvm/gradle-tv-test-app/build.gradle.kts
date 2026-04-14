plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    implementation(project(":capture"))
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.leanback:leanback:1.2.0")
}

android {
    namespace = "io.bitdrift.gradletvtestapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.bitdrift.gradletvtestapp"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "tv-dev"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        checkDependencies = true
        disable.add("GradleDependency")
        disable.add("AndroidGradlePluginVersion")
    }
}
