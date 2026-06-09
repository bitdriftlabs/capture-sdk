plugins {
    id("com.android.library")
}

group = "io.bitdrift"
version = "0.22.16"

android {
    namespace = "io.bitdrift.capture_flutter"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("io.bitdrift:capture:0.23.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
