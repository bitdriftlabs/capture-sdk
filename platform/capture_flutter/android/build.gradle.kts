import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Migrated to Flutter's Built-in Kotlin support (AGP 9+): the plugin no
// longer applies org.jetbrains.kotlin.android directly. See
// https://docs.flutter.dev/release/breaking-changes/migrate-to-built-in-kotlin/for-plugin-authors
plugins {
    id("com.android.library")
}

group = "io.bitdrift"
version = "0.24.1"

android {
    namespace = "io.bitdrift.capture_flutter"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("io.bitdrift:capture:0.24.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
