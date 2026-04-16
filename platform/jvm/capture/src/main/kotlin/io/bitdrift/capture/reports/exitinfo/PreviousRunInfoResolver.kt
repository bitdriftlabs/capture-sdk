// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.os.Build
import androidx.annotation.RequiresApi
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.IJvmCrashListener

/**
 * Provides [PreviousRunInfo] for the previous app session.
 *
 * On API >= 30, uses [ApplicationExitInfo] directly.
 * On API < 30, relies on manually persisted previous-run state, including when a JVM crash was recorded.
 */
internal class PreviousRunInfoResolver(
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider,
    preferences: IPreferences,
    captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler,
) : IPreviousRunInfoResolver,
    IJvmCrashListener {
    private val previousRunInfoLegacyStore by lazy { LegacyPreviousRunStateStore(preferences) }

    private val legacyPreviousRunState: LegacyPreviousRunState?

    init {
        if (isBelowApi30()) {
            legacyPreviousRunState = previousRunInfoLegacyStore.getPreviousState()
            previousRunInfoLegacyStore.writeState(LegacyPreviousRunState.Started)
            captureUncaughtExceptionHandler.install(this)
        } else {
            legacyPreviousRunState = null
        }
    }

    override fun get(): PreviousRunInfo? =
        if (isBelowApi30()) {
            getFromLegacyState()
        } else {
            getFromAppExitInfo()
        }

    /**
     * Will only trigger below OS 11
     */
    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        previousRunInfoLegacyStore.writeState(LegacyPreviousRunState.JvmCrash)
    }

    private fun isBelowApi30() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    private fun getFromLegacyState(): PreviousRunInfo? {
        val previousState = legacyPreviousRunState ?: return null

        return when (previousState) {
            LegacyPreviousRunState.JvmCrash ->
                PreviousRunInfo(
                    hasFatallyTerminated = true,
                    terminationReason = ExitReason.JvmCrash,
                )

            LegacyPreviousRunState.Started -> PreviousRunInfo(hasFatallyTerminated = false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getFromAppExitInfo(): PreviousRunInfo? =
        when (val result = latestAppExitInfoProvider.get()) {
            is LatestAppExitReasonResult.Valid -> {
                val reason = result.applicationExitInfo.reason.toExitReason()
                PreviousRunInfo(
                    hasFatallyTerminated = isFatalReason(reason),
                    terminationReason = reason,
                )
            }
            is LatestAppExitReasonResult.None -> PreviousRunInfo(hasFatallyTerminated = false)
            is LatestAppExitReasonResult.Error -> null
        }

    private fun isFatalReason(exitReason: ExitReason): Boolean =
        exitReason == ExitReason.JvmCrash ||
            exitReason == ExitReason.NativeCrash ||
            exitReason == ExitReason.AppNotResponding
}

/**
 * Snapshot of the previous app run status.
 *
 * On API 30, native crashes will be reported as a fatal termination reason but will not
 * trigger an `onBeforeSend` callback with the crash report. The `onBeforeSend` callback
 * for native crashes is only available on API >= 31.
 *
 * @property hasFatallyTerminated Whether the previous run ended in a fatal termination.
 * @property terminationReason Platform exit reason when available.
 */
data class PreviousRunInfo(
    val hasFatallyTerminated: Boolean,
    val terminationReason: ExitReason? = null,
)

internal class LegacyPreviousRunStateStore(
    private val preferences: IPreferences,
) {
    fun getPreviousState(): LegacyPreviousRunState? = preferences.getString(STATE_KEY)?.let(LegacyPreviousRunState::fromStorageValue)

    fun writeState(state: LegacyPreviousRunState) {
        val blocking = state == LegacyPreviousRunState.JvmCrash
        preferences.setString(STATE_KEY, state.value, blocking = blocking)
    }

    private companion object {
        private const val STATE_KEY = "io.bitdrift.capture.previous_run_info.state"
    }
}

internal enum class LegacyPreviousRunState(
    /** Stable text representation*/
    val value: String,
) {
    Started("started"),
    JvmCrash("jvm_crash"),
    ;

    companion object {
        fun fromStorageValue(value: String): LegacyPreviousRunState? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Contract for producing [PreviousRunInfo] from app exit signals.
 */
internal fun interface IPreviousRunInfoResolver {
    /**
     * Returns previous run status, or `null` when previous run info is not available.
     */
    fun get(): PreviousRunInfo?
}
