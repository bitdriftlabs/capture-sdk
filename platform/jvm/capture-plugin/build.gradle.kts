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

    signAllPublications()

    pom {
        name.set("CapturePlugin")
        description.set("Capture Gradle plugin.")
        url.set("https://bitdrift.io")
        licenses {
            license {
                name.set("BITDRIFT SOFTWARE DEVELOPMENT KIT LICENSE AGREEMENT")
                url.set("https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture-timber/${findProperty("VERSION_NAME")}/LICENSE.txt")
                distribution.set("repo")
            }
            license {
                name.set("NOTICE")
                url.set("https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture-timber/${findProperty("VERSION_NAME")}/NOTICE.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("bitdriftlabs")
                name.set("Bitdrift, Inc.")
                url.set("https://github.com/bitdriftlabs")
                email.set("info@bitdrift.io")
            }
            developer {
                id.set("murki")
                name.set("Miguel Angel Juárez López")
                url.set("https://github.com/murki")
                email.set("miguel@bitdrift.io")
            }
            scm {
                connection.set("scm:git:git://github.com/bitdriftlabs/capture-sdk.git")
                developerConnection.set("scm:git:ssh://git@github.com:bitdriftlabs/capture-sdk.git")
                url.set("https://github.com/bitdriftlabs/capture-sdk")
            }
        }
    }
}

group = "io.bitdrift"
version = "0.1.0"
