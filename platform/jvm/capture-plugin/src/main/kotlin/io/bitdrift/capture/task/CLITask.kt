package io.bitdrift.capture.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.net.URI

/// Example BD CLI task that prints the help text
abstract class CLIHelpTask : CLITask() {
    @TaskAction
    fun action() {
        println(runBDCLI(listOf("help")))
    }
}

abstract class CLITask : DefaultTask() {
    @Internal
    val bdcliVersion = "0.1.32"
    @Internal
    val bdcliDownloadLoc = URI.create("https://dl.bitdrift.io/bd-cli/${bdcliVersion}/bd-cli-mac-universal-apple-darwin.tar.gz/bd")
    @Internal
    val binPath = project.getLayout().getBuildDirectory().get().dir("bin")
    @Internal
    val bdcliPath: File = binPath.file("bd").asFile

    fun runBDCLI(args: List<String>): String {
        if(!bdcliPath.exists()) {
            downloadBDCLI()
        }
        return runCommand(listOf(bdcliPath.absolutePath) + args)
    }

    fun runCommand(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val outputText = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) {
            throw RuntimeException("Command ${command} failed: ${outputText}")
        }
        return outputText
    }

    fun downloadBDCLI() {
        if(!binPath.asFile.exists() && !binPath.asFile.mkdirs()) {
            throw IOException("Could not create path ${binPath.asFile.absolutePath}")
        }
        bdcliPath.writeBytes(bdcliDownloadLoc.toURL().readBytes())
        if(!bdcliPath.setExecutable(true)) {
            throw IOException("Could not mark ${bdcliPath.absolutePath} as executable")
        }
    }
}
