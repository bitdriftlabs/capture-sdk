import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("dependency-license-config")
    id("java-gradle-plugin")

    alias(libs.plugins.dokka) // Must be applied here for publish plugin.
    alias(libs.plugins.maven.publish)
    signing
}

dependencies {
    compileOnly(libs.android.tools.gradle)
    compileOnly(libs.asm.commons)
    compileOnly(libs.asm.util)

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.android.tools.gradle)
    testImplementation(libs.junit)
    testImplementation(libs.asm.commons)
    testImplementation(libs.asm.util)
    testImplementation(libs.mockito.kotlin)
}

gradlePlugin {
    plugins {
        create("capturePlugin") {
            id = "io.bitdrift.capture.capture-plugin"
            implementationClass = "io.bitdrift.capture.CapturePlugin"
        }
    }
}

mavenPublishing {
    configureBasedOnAppliedPlugins()

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
}

signing {
    sign(publishing.publications)
}

group = "io.bitdrift"
version = "0.1.0"
