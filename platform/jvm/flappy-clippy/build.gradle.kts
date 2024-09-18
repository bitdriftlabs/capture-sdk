plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    implementation(project(":capture"))
    implementation(project(":capture-timber"))
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("androidx.core:core-splashscreen:1.0.0-alpha01")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation(libs.androidx.material3.android)
    implementation(project(":common"))

    debugImplementation("androidx.compose.ui:ui-test-manifest:1.4.0")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")
}

android {
    namespace = "io.bitdrift.flappyclippy"
    compileSdk = 34

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    defaultConfig {
        applicationId = "io.bitdrift.flappyclippy"
        minSdk = 24
        targetSdk = 34
        versionCode = 66
        versionName = "1.0"
    }

    signingConfigs {
        // Important: change the keystore for a production deployment
        val userKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
        val localKeystore = rootProject.file("debug_2.keystore")
        val hasKeyInfo = userKeystore.exists()
        create("release") {
            storeFile = if (hasKeyInfo) userKeystore else localKeystore
            storePassword = if (hasKeyInfo) "android" else System.getenv("compose_store_password")
            keyAlias = if (hasKeyInfo) "androiddebugkey" else System.getenv("compose_key_alias")
            keyPassword = if (hasKeyInfo) "android" else System.getenv("compose_key_password")
        }
    }

    buildTypes {
        debug {
            // This can be enable to test proguard rules while debugging
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "test-proguard-rules.pro")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "test-proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
