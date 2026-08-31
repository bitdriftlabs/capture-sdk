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
import kotlin.test.assertFalse
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
        assertTrue(recordedArgs.readText().contains("--api-key test-api-key"))
    }

    @Test
    fun `falls back to legacy API key environment variable`() {
        val projectDir = tempDir.root
        val buildDir = File(projectDir, "build")
        val binDir = File(buildDir, "bin")
        val recordedArgs = File(buildDir, "bd-cli-args.txt")

        writeSettingsFile(projectDir)
        writeBuildFile(projectDir)
        writeManifestAndMappingFiles(buildDir)
        writeFakeBdExecutable(binDir, recordedArgs)

        val result = runGradle(projectDir, "bdUploadMapping", environment = mapOf("API_KEY" to "legacy-api-key"))
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(recordedArgs.readText().contains("--api-key legacy-api-key"))
    }

    @Test
    fun `uses BITDRIFT API key for source map uploads`() {
        val projectDir = tempDir.root
        val buildDir = File(projectDir, "build")
        val binDir = File(buildDir, "bin")
        val recordedArgs = File(buildDir, "bd-cli-args.txt")

        writeSettingsFile(projectDir)
        writeBuildFile(projectDir)
        writeSourceMapFiles(buildDir)
        writeFakeBdExecutable(binDir, recordedArgs)

        val result = runGradle(projectDir, "bdUploadSourceMap")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(recordedArgs.readText().contains("--api-key test-api-key"))
    }

    @Test
    fun `requires a non-empty API key for every upload task`() {
        val projectDir = tempDir.root
        val buildDir = File(projectDir, "build")
        val binDir = File(buildDir, "bin")
        val recordedArgs = File(buildDir, "bd-cli-args.txt")

        writeSettingsFile(projectDir)
        writeBuildFile(projectDir)
        writeManifestAndMappingFiles(buildDir)
        writeNativeLibraries(buildDir)
        writeSourceMapFiles(buildDir)
        writeFakeBdExecutable(binDir, recordedArgs)

        listOf("bdUploadMapping", "bdUploadSymbols", "bdUploadSourceMap").forEach { task ->
            val result = runGradle(projectDir, task, environment = emptyMap(), shouldFail = true)
            assertTrue(
                result.output.contains(
                    "Environment variable BITDRIFT_API_KEY or API_KEY must be set with a non-empty bitdrift API key before running this task.",
                ),
            )
        }

        val result =
            runGradle(
                projectDir,
                "bdUploadMapping",
                environment = mapOf("BITDRIFT_API_KEY" to "   "),
                shouldFail = true,
            )
        assertTrue(result.output.contains("must be set with a non-empty bitdrift API key"))
    }

    @Test
    fun `masks API keys when the CLI command fails`() {
        val projectDir = tempDir.root
        val buildDir = File(projectDir, "build")
        val binDir = File(buildDir, "bin")
        val recordedArgs = File(buildDir, "bd-cli-args.txt")
        val apiKey = "secret-api-key"

        writeSettingsFile(projectDir)
        writeBuildFile(projectDir)
        writeManifestAndMappingFiles(buildDir)
        writeFakeBdExecutable(binDir, recordedArgs, exitCode = 1)

        val result = runGradle(projectDir, "bdUploadMapping", environment = mapOf("BITDRIFT_API_KEY" to apiKey), shouldFail = true)
        assertTrue(result.output.contains("--base-domain, api.bitdrift.dev, --api-key, *****, debug-files, upload-proguard"))
        assertFalse(result.output.contains(apiKey))
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

    private fun writeSourceMapFiles(buildDir: File) {
        val sourceMapFile = File(buildDir, "generated/sourcemaps/react/release/index.android.bundle.map")
        sourceMapFile.parentFile.mkdirs()
        sourceMapFile.writeText("{}\n")

        val bundleFile = File(buildDir, "generated/assets/createBundleReleaseJsAndAssets/index.android.bundle")
        bundleFile.parentFile.mkdirs()
        bundleFile.writeText("console.log('test')\n")
    }

    private fun writeNativeLibraries(buildDir: File) {
        val nativeLibsDir = File(buildDir, "intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a")
        nativeLibsDir.mkdirs()
        File(nativeLibsDir, "libtest.so").writeText("test\n")
    }

    private fun writeFakeBdExecutable(
        binDir: File,
        recordedArgs: File,
        exitCode: Int = 0,
    ) {
        binDir.mkdirs()
        val bd = File(binDir, "bd")
        bd.writeText(
            """
            #!/bin/sh
            echo "$@" > "${recordedArgs.absolutePath}"
            exit $exitCode
            """.trimIndent() + "\n",
        )
        bd.setExecutable(true)
        File(binDir, "bd.version").writeText("0.2.23")
    }

    private fun runGradle(
        projectDir: File,
        vararg args: String,
        environment: Map<String, String> = mapOf("BITDRIFT_API_KEY" to "test-api-key"),
        shouldFail: Boolean = false,
    ): BuildResult {
        val runner = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(*args, "-Pandroid.injected.build.api=33", "-Pandroid.injected.build.abi=arm64-v8a")
            .withPluginClasspath()
            .withEnvironment(environment)
        return if (shouldFail) runner.buildAndFail() else runner.build()
    }
}
