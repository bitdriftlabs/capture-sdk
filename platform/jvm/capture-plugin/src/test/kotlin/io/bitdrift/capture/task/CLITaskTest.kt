// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.task

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertTrue

class CLITaskTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `adds base domain to cli invocation`() {
        val projectDir = tempDir.root
        val buildDir = File(projectDir, "build")
        val binDir = File(buildDir, "bin")
        val recordedArgs = File(buildDir, "bd-cli-args.txt")

        writeSettingsFile(projectDir)
        writeBuildFile(projectDir)
        writeManifestAndMappingFiles(buildDir)
        writeFakeBdExecutable(binDir, recordedArgs)

        val result = runGradle(projectDir, "bdUploadMapping")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(recordedArgs.readText().contains("--base-domain api.bitdrift.dev"))
    }

    private fun writeSettingsFile(projectDir: File) {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    gradlePluginPortal()
                    mavenCentral()
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.id == "com.android.application" || requested.id.id == "com.android.library") {
                            useModule("com.android.tools.build:gradle:${'$'}{requested.version}")
                        }
                    }
                }
            }

            rootProject.name = "cli-test"
            """.trimIndent() + "\n",
        )
    }

    private fun writeBuildFile(projectDir: File) {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.12.0"
                id("io.bitdrift.capture-plugin")
            }

            buildscript {
                repositories {
                    google()
                    mavenCentral()
                }
                dependencies {
                    classpath("com.android.tools.build:gradle:8.12.0")
                }
            }

            android {
                namespace = "io.bitdrift.test"
                compileSdk = 33
                defaultConfig {
                    applicationId = "io.bitdrift.test"
                    minSdk = 23
                    targetSdk = 33
                    versionCode = 1
                    versionName = "1.0"
                }
            }

            bitdrift {
                baseDomain = "api.bitdrift.dev"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeManifestAndMappingFiles(buildDir: File) {
        val manifestDir = File(buildDir, "intermediates/packaged_manifests/release/processReleaseManifestForPackage")
        manifestDir.mkdirs()
        File(manifestDir, "AndroidManifest.xml").writeText(
            """
            <manifest package="io.bitdrift.test" android:versionCode="1" android:versionName="1.0" xmlns:android="http://schemas.android.com/apk/res/android" />
            """.trimIndent() + "\n",
        )

        val mappingDir = File(buildDir, "outputs/mapping/release")
        mappingDir.mkdirs()
        File(mappingDir, "mapping.txt").writeText("# mapping\n")
    }

    private fun writeFakeBdExecutable(
        binDir: File,
        recordedArgs: File,
    ) {
        binDir.mkdirs()
        val bd = File(binDir, "bd")
        bd.writeText(
            """
            #!/bin/sh
            echo "$@" > "${recordedArgs.absolutePath}"
            exit 0
            """.trimIndent() + "\n",
        )
        bd.setExecutable(true)
    }

    private fun runGradle(
        projectDir: File,
        vararg args: String,
    ): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(*args, "-Pandroid.injected.build.api=33", "-Pandroid.injected.build.abi=arm64-v8a")
            .withPluginClasspath()
            .withEnvironment(mapOf("API_KEY" to "test-api-key"))
            .build()
}
