// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

abstract class CLIUploadMappingTask : CLITask() {
    @TaskAction
    fun action() {
        // e.g. build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml
        val androidManifestXmlFile = buildDir.get().asFile.mostRecentSubfileNamed("AndroidManifest.xml")
        // e.g. build/outputs/mapping/release/mapping.txt
        val mappingTxtFile = buildDir.get().asFile.mostRecentSubfileNamed("mapping.txt")

        val manifest = androidManifestXmlFile.asXmlDocument().documentElement
        val appId = manifest.getAttribute("package")
        val versionCode = manifest.getAttribute("android:versionCode")
        val versionName = manifest.getAttribute("android:versionName")

        runBDCLI(
            listOf(
                "debug-files",
                "upload-proguard",
                "--app-id",
                appId,
                "--app-version",
                versionName,
                "--version-code",
                versionCode,
                mappingTxtFile.absolutePath,
            ),
        )
    }
}

abstract class CLIUploadSymbolsTask : CLITask() {
    @TaskAction
    fun action() {
        // e.g. build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs
        val nativeLibsDir = buildDir.get().asFile.mostRecentSubfileMatching(".*merge.*NativeLibs".toRegex())
        runBDCLI(listOf("debug-files", "upload", nativeLibsDir.absolutePath))
    }
}

abstract class CLIUploadSourceMapTask : CLITask() {
    @TaskAction
    fun action() {
        // e.g. build/generated/sourcemaps/react/release/index.android.bundle.map
        val sourceMapFile = buildDir.get().asFile.mostRecentSubfileNamedOrNull("index.android.bundle.map")
        // e.g. build/generated/assets/createBundleReleaseJsAndAssets/index.android.bundle
        val bundleFile = buildDir.get().asFile.mostRecentSubfileNamedOrNull("index.android.bundle")

        if (sourceMapFile == null || bundleFile == null) {
            println("No React Native sourcemaps found, skipping upload")
            return
        }

        runBDCLI(
            listOf(
                "debug-files",
                "upload-source-map",
                "--source-map",
                sourceMapFile.absolutePath,
                "--bundle",
                bundleFile.absolutePath,
            ),
        )
    }
}

abstract class CLITask : DefaultTask() {
    @get:Internal
    abstract val buildDir: DirectoryProperty

    @get:Internal
    abstract val baseDomain: Property<String>

    @get:Internal
    val bdcliFile: File
        get() = buildDir.file("bin/bd").get().asFile

    @get:Internal
    val downloader: BDCLIDownloader
        get() = BDCLIDownloader(bdcliFile)

    /**
     * Runs the bd CLI with the given command arguments. Global flags (`--base-domain` and
     * `--api-key`) are added here, so callers must not include them.
     *
     * The API key is resolved and validated before anything is downloaded, so a misconfigured
     * build fails immediately instead of after fetching the binary.
     */
    fun runBDCLI(subcommand: List<String>) {
        val apiKey = envOrNull(BITDRIFT_API_KEY_ENV_VAR_NAME)
            ?: envOrNull(DEPRECATED_API_KEY_ENV_VAR_NAME)
            ?: throw GradleException(
                "Environment variable $BITDRIFT_API_KEY_ENV_VAR_NAME or $DEPRECATED_API_KEY_ENV_VAR_NAME must be set with a non-empty bitdrift API key before running this task."
            )

        downloader.downloadIfNeeded()

        runCommand(listOf(bdcliFile.absolutePath) + globalArguments(apiKey) + subcommand)
    }

    fun runCommand(command: List<String>) {
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        process.inputStream.transferTo(System.out)
        if (process.waitFor() != 0) {
            throw GradleException("Command ${command.maskedCommandArguments()} failed")
        }
    }

    private fun envOrNull(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun globalArguments(apiKey: String): List<String> {
        val apiKeyArguments = listOf(API_KEY_FLAG, apiKey)

        val baseDomain = baseDomain.orNull?.trim().orEmpty()
        return if (baseDomain.isNotEmpty()) {
            listOf(BASE_DOMAIN_FLAG, baseDomain) + apiKeyArguments
        } else {
            apiKeyArguments
        }
    }

    private fun List<String>.maskedCommandArguments(): List<String> =
        mapIndexed { index, argument ->
            if (index > 0 && this[index - 1] == API_KEY_FLAG) "*****" else argument
        }

    private companion object {
        private const val API_KEY_FLAG = "--api-key"
        private const val BASE_DOMAIN_FLAG = "--base-domain"
        private const val BITDRIFT_API_KEY_ENV_VAR_NAME = "BITDRIFT_API_KEY"
        private const val DEPRECATED_API_KEY_ENV_VAR_NAME = "API_KEY"
    }
}

class BDCLIDownloader(
    val executableFilePath: File,
    private val download: (URI) -> ByteArray = { it.toURL().readBytes() },
) {
    val bdcliVersion = "0.2.23"
    val bdcliDownloadLoc: URI = URI.create("https://dl.bitdrift.io/bd-cli/$bdcliVersion/${downloadFilename()}/bd")

    private enum class OSType {
        MacIntel,
        MacArm,
        LinuxIntel,
    }

    private fun osType(): OSType {
        val osName = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")
        return when (osName) {
            "Mac OS X" ->
                when (arch) {
                    "aarch64" -> OSType.MacArm
                    else -> OSType.MacIntel
                }
            "Linux" -> OSType.LinuxIntel
            else -> throw IllegalStateException(
                "Could not determine running system (got $osName, $arch). Only Mac (Intel, Arm) and linux (Intel) are currently supported",
            )
        }
    }

    private fun downloadFilename(): String =
        when (osType()) {
            OSType.MacArm -> "bd-cli-mac-arm64.tar.gz"
            OSType.MacIntel -> "bd-cli-mac-x86_64.tar.gz"
            OSType.LinuxIntel -> "bd-cli-linux-x86_64.tar.gz"
        }

    fun downloadIfNeeded() {
        synchronized(lock) {
            if (isCurrentVersionInstalled()) return

            val parentDir = executableFilePath.parentFile
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw IOException("Could not create path '${parentDir.absolutePath}' to contain the downloaded binary")
            }

            val lockFilePath: Path = parentDir.toPath().resolve("bd.install.lock")
            FileChannel.open(
                lockFilePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { fileChannel ->
                fileChannel.lock().use {
                    if (isCurrentVersionInstalled()) return

                    val tempPath = parentDir.toPath().resolve("bd.${UUID.randomUUID()}.tmp")
                    try {
                        Files.write(tempPath, download(bdcliDownloadLoc))
                        tempPath.markAsExecutable()
                        tempPath.moveSafelyTo(executableFilePath.toPath())
                        cliVersionFile().writeText(bdcliVersion)
                    } catch (e: Exception) {
                        runCatching { Files.deleteIfExists(tempPath) }
                        throw IOException(
                            "Failed to download bd cli tool from $bdcliDownloadLoc",
                            e
                        )
                    }
                }
            }
        }
    }

    private fun isCurrentVersionInstalled(): Boolean =
        executableFilePath.exists() && cliVersionFile().takeIf(File::exists)?.readText()?.trim() == bdcliVersion

    private fun cliVersionFile(): File = File(executableFilePath.parentFile, "bd.version")

    private fun Path.moveSafelyTo(dest: Path) {
        try {
            Files.move(
                this,
                dest,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(this, dest)
        }
    }

    private fun Path.markAsExecutable() {
        val tempFile = this.toFile()
        if (!tempFile.setExecutable(true, false)) {
            throw IOException("Could not mark ${tempFile.absolutePath} as executable")
        }
    }

    private companion object {
        private val lock = Any()
    }
}

fun File.asXmlDocument(): Document {
    try {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this)
    } catch (e: Exception) {
        throw IOException("Could not parse XML file $this", e)
    }
}

fun File.mostRecentSubfileNamed(name: String): File {
    try {
        return this.subfilesNamed(name).mostRecent()
    } catch (e: Exception) {
        throw IOException("Could not find any file named '$name' in path or subpath of '$this", e)
    }
}

fun File.mostRecentSubfileNamedOrNull(name: String): File? = this.subfilesNamed(name).mostRecentOrNull()

fun File.subfilesNamed(name: String): Sequence<File> = this.walkTopDown().filter { it.name == name }

fun File.mostRecentSubfileMatching(regex: Regex): File {
    try {
        return this.subfilesMatching(regex).mostRecent()
    } catch (e: Exception) {
        throw IOException("Could not find any file matching regex '$regex' in path or subpath of '$this", e)
    }
}

fun File.subfilesMatching(regex: Regex): Sequence<File> = this.walkTopDown().filter { regex.matches(it.name) }

fun Sequence<File>.mostRecent(): File = this.sortedBy { it.lastModified() }.last()

fun Sequence<File>.mostRecentOrNull(): File? = this.sortedBy { it.lastModified() }.lastOrNull()
