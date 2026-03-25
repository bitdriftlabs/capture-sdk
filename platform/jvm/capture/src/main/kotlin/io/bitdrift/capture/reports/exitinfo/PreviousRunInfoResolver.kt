// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ActivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import io.bitdrift.capture.IInternalLogger
import java.io.File

/**
 * Provides [PreviousRunInfo] for the previous app session.
 *
 * On API >= 30, uses [ApplicationExitInfo] directly.
 * On API < 30, uses a persisted state file as a fallback signal.
 *
 * On startup, [initLegacyWatcher] must be called to snapshot the legacy state before it is
 * reset for the current run.
 */
internal class PreviousRunInfoResolver(
    private val internalLogger: IInternalLogger,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
) : IPreviousRunInfoResolver {
    private var legacyStateFile: File? = null
    private var legacySnapshot: PreviousRunInfo? = null

    override fun get(activityManager: ActivityManager): PreviousRunInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getFromAppExitInfo(activityManager)
        } else {
            legacySnapshot
        }

    /**
     * Snapshots the legacy persisted state (< API 30) and resets it for the
     * current run. No-op on API >= 30 where [ApplicationExitInfo] is used.
     */
    override fun initLegacyWatcher(sdkDirectory: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return

        legacyStateFile = File(sdkDirectory, "reports/previous_run_info.state")
        legacySnapshot = readPersistedState()
        writePersistedState(PreviousRunInfo(hasFatallyTerminated = false))
    }

    /**
     * Persists a JVM crash marker so the next launch can detect it.
     * No-op on API >= 30.
     */
    override fun persistJvmCrashState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return

        writePersistedState(PreviousRunInfo(hasFatallyTerminated = true, reason = ExitReason.JvmCrash))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getFromAppExitInfo(activityManager: ActivityManager): PreviousRunInfo? =
        when (val result = latestAppExitInfoProvider.get(activityManager)) {
            is LatestAppExitReasonResult.Valid -> {
                val reason = result.applicationExitInfo.reason.toExitReason()
                PreviousRunInfo(
                    hasFatallyTerminated = isFatalReason(reason),
                    reason = reason,
                )
            }
            is LatestAppExitReasonResult.None -> PreviousRunInfo(hasFatallyTerminated = false)
            is LatestAppExitReasonResult.Error -> null
        }

    private fun readPersistedState(): PreviousRunInfo? {
        val file =
            legacyStateFile ?: run {
                internalLogger.logInternalError { ERROR_STATE_FILE_NOT_INITIALIZED }
                return null
            }
        // Upon initial app installation there won't be a state file
        if (!file.exists()) return PreviousRunInfo(hasFatallyTerminated = false)

        return try {
            val values = parseKeyValueFile(file)
            val hasCrashed = values[KEY_HAS_FATAL_TERMINATION]?.toBooleanStrictOrNull() ?: false
            val reason = values[KEY_REASON]?.ifEmpty { null }?.let(ExitReason::fromValue)
            PreviousRunInfo(hasFatallyTerminated = hasCrashed, reason = reason)
        } catch (e: Exception) {
            internalLogger.logInternalError(e) { "Failed to read PreviousRunInfo persisted state" }
            null
        }
    }

    private fun parseKeyValueFile(file: File): Map<String, String> =
        file
            .readLines()
            .filter { KEY_VALUE_DELIMITER in it }
            .associate { line ->
                line.substringBefore(KEY_VALUE_DELIMITER) to line.substringAfter(KEY_VALUE_DELIMITER)
            }

    private fun writePersistedState(info: PreviousRunInfo) {
        val file =
            legacyStateFile ?: run {
                internalLogger.logInternalError { ERROR_STATE_FILE_NOT_INITIALIZED }
                return
            }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                "$KEY_HAS_FATAL_TERMINATION$KEY_VALUE_DELIMITER${info.hasFatallyTerminated}\n" +
                    "$KEY_REASON$KEY_VALUE_DELIMITER${info.reason?.value.orEmpty()}\n",
            )
        }.onFailure {
            internalLogger.logInternalError(it) {
                "Failed to write PreviousRunInfo persisted state"
            }
        }
    }

    private fun isFatalReason(exitReason: ExitReason): Boolean =
        exitReason == ExitReason.JvmCrash ||
            exitReason == ExitReason.NativeCrash ||
            exitReason == ExitReason.AppNotResponding

    private companion object {
        private const val KEY_VALUE_DELIMITER = '='
        private const val KEY_HAS_FATAL_TERMINATION = "hasFatallyTerminated"
        private const val KEY_REASON = "reason"
        private const val ERROR_STATE_FILE_NOT_INITIALIZED = "Legacy state file not initialized"
    }
}

/**
 * Snapshot of the previous app run status.
 *
 * On API 30, native crashes will be reported as a fatal termination reason but will not
 * trigger an `onBeforeSend` callback with the crash report. The `onBeforeSend` callback
 * for native crashes is only available on API >= 31.
 *
 * @property hasFatallyTerminated Whether the previous run ended in a fatal termination.
 * @property reason Platform exit reason when available.
 */
data class PreviousRunInfo(
    val hasFatallyTerminated: Boolean,
    val reason: ExitReason? = null,
)

/**
 * Contract for producing [PreviousRunInfo] from app exit signals.
 */
interface IPreviousRunInfoResolver {
    /**
     * Returns previous run status, or `null` when previous run info is not applicable.
     */
    fun get(activityManager: ActivityManager): PreviousRunInfo?

    /**
     * Initializes the legacy crash state watcher for OS <11
     */
    fun initLegacyWatcher(sdkDirectory: String)

    /**
     * Persist JVM crash state for OS <11
     */
    fun persistJvmCrashState()
}
