// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.gradletestapp.init

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ProcessLifecycleOwner
import io.bitdrift.capture.Capture
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandHandle
import io.bitdrift.capture.commands.argument
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.reports.persistence.IssueReporterStore
import io.bitdrift.capture.reports.processor.ReportProcessingSession
import io.bitdrift.capture.reports.profiling.CrashProfileKind
import io.bitdrift.capture.reports.profiling.CrashProfileSource
import io.bitdrift.capture.utils.SdkDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/** Fixture controls registered only in explicitly opted-in E2E sample builds. */
@OptIn(ExperimentalBitdriftApi::class)
internal object E2eCommands {
    fun register(context: Context): List<CommandHandle> =
        listOf(
            Capture.Logger.registerCommand("e2e_file_replay") {
                val name = argument<String>("file")
                val contentType = argument<String>("content_type")
                try {
                    success(
                        attachment = replayFile(context, name, contentType),
                        context = mapOf("source" to "fixture_replay"),
                    )
                } catch (e: IOException) {
                    error(listOfNotNull("fixture_replay_failed", e.message?.takeUnless { it.isBlank() }).joinToString(": "))
                }
            },
            Capture.Logger.registerCommand("e2e_report") {
                if (Build.VERSION.SDK_INT < 36) return@registerCommand error("requires_api_36")
                try {
                    val path = withContext(Dispatchers.IO) { persistParent(context) }
                    success(context = mapOf("report_file" to path, "source" to "synthetic_anr"))
                } catch (e: IOException) {
                    error(listOfNotNull("fixture_report_failed", e.message?.takeUnless { it.isBlank() }).joinToString(": "))
                }
            },
            Capture.Logger.registerCommand("e2e_import_profile") {
                if (Build.VERSION.SDK_INT < 36) return@registerCommand error("requires_api_36")
                val name = argument<String>("file")
                try {
                    val source = fixture(context, name)
                    val imported =
                        suspendCancellableCoroutine { continuation ->
                            Capture.Logger.importCrashProfile(source, CrashProfileKind.ANR) { result ->
                                continuation.resume(result)
                            }
                        }
                    when (imported) {
                        is CaptureResult.Success -> success(context = mapOf("private_copy_persisted" to "true"))
                        is CaptureResult.Failure ->
                            error(
                                listOfNotNull("fixture_import_failed", imported.error.message.takeUnless { it.isBlank() }).joinToString(": "),
                            )
                    }
                } catch (e: IOException) {
                    error(listOfNotNull("fixture_import_failed", e.message?.takeUnless { it.isBlank() }).joinToString(": "))
                }
            },
            Capture.Logger.registerCommand("e2e_log") {
                val marker = argument<String>("marker")
                Capture.Logger.logInfo(mapOf("m12_phase2" to marker)) { marker }
                success()
            },
        )

    internal suspend fun replayFile(
        context: Context,
        name: String,
        contentType: String,
    ): CommandAttachment {
        var unclaimed: File? = null
        try {
            val attachment =
                withContext(Dispatchers.IO) {
                    val source = fixture(context, name)
                    val owned = File.createTempFile("e2e-attachment-", ".bin", context.filesDir)
                    unclaimed = owned
                    val size = source.length()
                    source.inputStream().use { input ->
                        owned.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (owned.length() != size) throw IOException("fixture changed while copying")
                    CommandAttachment.file(owned, contentType)
                }
            unclaimed = null
            return attachment
        } finally {
            unclaimed?.let { file ->
                withContext(NonCancellable + Dispatchers.IO) {
                    if (file.exists() && !file.delete()) Log.w("E2eCommands", "Could not delete ${file.path}")
                }
            }
        }
    }

    private fun fixture(
        context: Context,
        name: String,
    ): File {
        val directory = context.getExternalFilesDir("e2e-inputs") ?: throw IOException("external storage is unavailable")
        val source = File(directory, name).canonicalFile
        if (source.parentFile != directory.canonicalFile || !source.isFile) {
            throw IOException("fixture must be a regular file directly under e2e-inputs")
        }
        return source
    }

    @RequiresApi(36)
    private fun persistParent(context: Context): String {
        val logger = Capture.logger() as? LoggerImpl ?: throw IOException("SDK is not initialized")
        val source =
            CrashProfileSource.current(context) { facts ->
                CaptureJniLibrary.encodeCrashProfileSource(
                    facts.pid,
                    facts.bootCount,
                    facts.processStartElapsedNs,
                    facts.wallNs,
                    facts.elapsedNs,
                )
            } ?: throw IOException("crash profile source facts are unavailable")
        val destination = IssueReporterStore(SdkDirectory.getPath(context)).generateNonFatalIssueFilePath()
        // A synthetic parent goes through normal JNI persistence and report discovery, not backend injection.
        CaptureJniLibrary.processAndPersistANR(
            logger.loggerId,
            null,
            System.currentTimeMillis(),
            destination,
            context.cacheDir.path,
            ClientAttributes(context, ProcessLifecycleOwner.get()),
            "foreground",
            "M12 native file handoff smoke",
            0,
            true,
            Process.myPid(),
            source,
        )
        if (!File(destination).isFile) throw IOException("SDK did not persist the report")
        logger.processIssueReports(ReportProcessingSession.Current)
        return File(destination).name
    }
}
