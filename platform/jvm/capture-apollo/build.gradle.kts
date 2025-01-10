plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)

    // Publish
    alias(libs.plugins.dokka) // Must be applied here for publish plugin.
    alias(libs.plugins.maven.publish)
    signing

    id("dependency-license-config")
}

group = "io.bitdrift"

android {
    namespace = "io.bitdrift.capture.apollo"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        quiet = false
        ignoreWarnings = false
        warningsAsErrors = true
        checkAllWarnings = true
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
    }
}

detekt {
    // Define the detekt configuration(s) you want to use.
    // Defaults to the default detekt configuration.
    config.setFrom("detekt.yml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude {
        it.file.absolutePath.contains("test/")
    }
}

tasks.preBuild {
    dependsOn("detekt")
}

dependencies {
    implementation(project(":capture"))
    implementation(libs.apollo.runtime)

    testImplementation(libs.truth)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.mockito.kotlin)
}

mavenPublishing {
    pom {
        name.set("CaptureApollo")
        description.set("Official Capture integration for Apollo (GraphQL).")
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
