plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.benchmark)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.bitdrift.microbenchmark"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // must be one of: 'None', 'StackSampling', or 'MethodTracing'
        testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "MethodTracing"
    }

    testOptions {
        targetSdk = 35
    }

    testBuildType = "release"
    buildTypes {
        debug {
            // Since isDebuggable can"t be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-proguard-rules.pro")
        }
        release {
            isDefault = true
        }
    }
}

dependencies {
    // the module containing code to benchmark
    androidTestImplementation(project(":capture"))

    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.1")
    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert

}