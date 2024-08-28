plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.apollo.graphql)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    implementation(project(":capture"))
    implementation(project(":capture-apollo3"))
    implementation(project(":capture-timber"))
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.compose.material:material:1.4.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("com.apollographql.apollo3:apollo-runtime:3.8.3")
    implementation("com.apollographql.apollo3:apollo-runtime:3.8.3")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation(libs.androidx.material3.android)

    debugImplementation("androidx.compose.ui:ui-test-manifest:1.4.0")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(project(":common"))
    androidTestImplementation("com.google.truth:truth:1.1.4")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.0")
    androidTestImplementation("androidx.tracing:tracing-ktx:1.0.0")
    androidTestImplementation("androidx.tracing:tracing:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.4.0")
}

android {
    namespace = "io.bitdrift.gradletestapp"
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
        applicationId = "io.bitdrift.gradletestapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 66
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Run lint checks on every build
    applicationVariants.configureEach {
        val lintTask = tasks.named("lint${name.replaceFirstChar(Char::titlecase)}")
        assembleProvider.get().dependsOn(lintTask)
    }
    lint {
        checkDependencies = true
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

// graphql
apollo {
    service("service") {
        // https://apollo-fullstack-tutorial.herokuapp.com/graphql
        packageName.set("com.example.rocketreserver")
    }
}
