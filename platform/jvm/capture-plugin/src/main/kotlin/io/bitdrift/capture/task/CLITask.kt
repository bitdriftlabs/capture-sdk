// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import java.io.File
import java.io.IOException
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

abstract class CLIUploadMappingTask : CLITask() {
    @TaskAction
    fun action() {
        // e.g. build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml
        val androidManifestXmlFile = buildDir.asFile.mostRecentSubfileNamed("AndroidManifest.xml")
        // e.g. build/outputs/mapping/release/mapping.txt
        val mappingTxtFile = buildDir.asFile.mostRecentSubfileNamed("mapping.txt")

        val manifest = androidManifestXmlFile.asXmlDocument().documentElement
        val appId = manifest.getAttribute("package")
        val versionCode = manifest.getAttribute("android:versionCode")
        val versionName = manifest.getAttribute("android:versionName")

        // retrieve key using preferred or legacy fallback env var if set
        val apiKey = System.getenv("BITDRIFT_API_KEY") ?: System.getenv("API_KEY")
        if (apiKey == null) {
            throw IllegalStateException("Environment variable BITDRIFT_API_KEY must be set to your Bitdrift API key before running this task")
        }

        runBDCLI(
            listOf(
                "debug-files",
                "upload-proguard",
                "--api-key",
                apiKey,
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
        val nativeLibsDir = buildDir.asFile.mostRecentSubfileMatching(".*merge.*NativeLibs".toRegex())
        runBDCLI(listOf("debug-files", "upload", nativeLibsDir.absolutePath))
    }
}

abstract class CLITask : DefaultTask() {
    @Internal
    val buildDir: Directory = project.layout.buildDirectory.get()

    @Internal
    val bdcliFile: File = buildDir.dir("bin").file("bd").asFile

    @Internal
    val downloader = BDCLIDownloader(bdcliFile)

    fun runBDCLI(args: List<String>) {
        downloader.downloadIfNeeded()
        runCommand(listOf(bdcliFile.absolutePath) + args)
    }

    fun runCommand(command: List<String>) {
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        process.inputStream.transferTo(System.out)
        if (process.waitFor() != 0) {
            throw RuntimeException("Command $command failed")
        }
    }
}

class BDCLIDownloader(
    val executableFilePath: File,
) {
    val bdcliVersion = "0.1.33-rc.1"
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
        if (executableFilePath.exists()) {
            return
        }
        val parentDir = executableFilePath.parentFile
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw IOException("Could not create path '${parentDir.absolutePath}' to contain the downloaded binary")
        }
        try {
            executableFilePath.writeBytes(bdcliDownloadLoc.toURL().readBytes())
        } catch (e: Exception) {
            throw IOException("Failed to download bd cli tool from $bdcliDownloadLoc", e)
        }
        if (!executableFilePath.setExecutable(true)) {
            throw IOException("Could not mark ${executableFilePath.absolutePath} as executable")
        }
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
