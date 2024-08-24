plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    // Pulls in dependencies to be used across shared configurations via files in src/main/kotlin
    // using precompiled script convention plugins: https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html
    implementation("com.github.jk1:gradle-license-report:2.9")
}
