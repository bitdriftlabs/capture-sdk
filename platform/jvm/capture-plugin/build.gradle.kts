import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("dependency-license-config")
    id("java-gradle-plugin")

    alias(libs.plugins.dokka) // Must be applied here for publish plugin.
    alias(libs.plugins.maven.publish)
    signing
}

dependencies {
    compileOnly("com.android.tools.build:gradle:7.4.0")
    compileOnly("org.ow2.asm:asm-commons:9.4")
    compileOnly("org.ow2.asm:asm-util:9.4")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("com.android.tools.build:gradle:7.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.ow2.asm:asm-commons:9.4")
    testImplementation("org.ow2.asm:asm-util:9.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
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
