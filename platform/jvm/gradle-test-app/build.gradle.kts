plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.apollo.graphql)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":capture"))
    implementation(project(":capture-apollo"))
    implementation(project(":capture-timber"))

    val composeBom = platform("androidx.compose:compose-bom:2025.12.00") // 1.10.0
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose (managed by BOM)
    implementation("androidx.compose.material3:material3-android")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")
    implementation("androidx.navigation:navigation-compose:2.5.3")

    // Other dependencies
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.apollographql.apollo:apollo-runtime:4.1.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("com.squareup.papa:papa:0.26")
    implementation("com.bugsnag:bugsnag-android:6.12.0")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.0")
    implementation("io.reactivex.rxjava3:rxjava:3.0.0")
    implementation("io.sentry:sentry-android:8.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(project(":common"))
    androidTestImplementation("com.google.truth:truth:1.1.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.0")
    androidTestImplementation("androidx.tracing:tracing-ktx:1.0.0")
    androidTestImplementation("androidx.tracing:tracing:1.0.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

android {
    namespace = "io.bitdrift.gradletestapp"
    compileSdk = 36

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "io.bitdrift.gradletestapp"
        minSdk = 23
        targetSdk = 36
        versionCode = 66
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // This needs to be set to access the strip tools to strip the shared libraries.
    ndkVersion = "27.2.12479018"

    buildTypes {
        debug {
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE" // Using this to reduce output .so size
            }
        }
        release {
            ndk {
                debugSymbolLevel = "FULL"
            }
            packaging {
                jniLibs {
                    keepDebugSymbols += "**/*.so"
                }
            }
        }
    }

    // Run lint checks on every build
    applicationVariants.configureEach {
        val lintTask = tasks.named("lint${name.replaceFirstChar(Char::titlecase)}")
        assembleProvider.get().dependsOn(lintTask)
    }
    lint {
        checkDependencies = true
        disable.add("GradleDependency")
        disable.add("AndroidGradlePluginVersion")
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
