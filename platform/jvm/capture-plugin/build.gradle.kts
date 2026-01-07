plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.maven.publish)
    id("dependency-license-config")
    id("java-gradle-plugin")
}

group = "io.bitdrift"

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
            id = "io.bitdrift.capture-plugin"
            implementationClass = "io.bitdrift.capture.CapturePlugin"
        }
    }
}

mavenPublishing {
    configureBasedOnAppliedPlugins()

    pom {
        name.set("CapturePlugin")
        description.set("Official Capture Gradle plugin.")
        url.set("https://bitdrift.io")
        licenses {
            license {
                name.set("BITDRIFT SOFTWARE DEVELOPMENT KIT LICENSE AGREEMENT")
                url.set("https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture-plugin/${findProperty("VERSION_NAME")}/LICENSE.txt")
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
            scm {
                connection.set("scm:git:git://github.com/bitdriftlabs/capture-sdk.git")
                developerConnection.set("scm:git:ssh://git@github.com:bitdriftlabs/capture-sdk.git")
                url.set("https://github.com/bitdriftlabs/capture-sdk")
            }
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repos/releases"))
        }
    }
}
