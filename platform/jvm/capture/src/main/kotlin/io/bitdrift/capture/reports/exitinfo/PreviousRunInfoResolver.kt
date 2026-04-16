// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.IJvmCrashListener
import io.bitdrift.capture.utils.BuildVersionChecker

/**
 * Provides [PreviousRunInfo] for the previous app session.
 *
 * On API >= 30, uses [android.app.ApplicationExitInfo] directly.
 * On API < 30, relies on manually persisted previous-run state and only reports JVM crashes as a
 * fatal termination reason.
 */
internal class PreviousRunInfoResolver(
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider,
    preferences: IPreferences,
    captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler,
    private val buildVersionChecker: BuildVersionChecker = BuildVersionChecker(),
) : IPreviousRunInfoResolver,
    IJvmCrashListener {
    private val previousRunInfoBelowApi30Store by lazy { PreviousRunInfoBelowApi30Store(preferences) }

    private val previousRunInfoBelowApi30State: PreviousRunInfoBelowApi30State?

    init {
        if (isBelowApi30()) {
            previousRunInfoBelowApi30State = previousRunInfoBelowApi30Store.getPreviousState()
            previousRunInfoBelowApi30Store.writeState(PreviousRunInfoBelowApi30State.Started)
            captureUncaughtExceptionHandler.install(this)
        } else {
            previousRunInfoBelowApi30State = null
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
        previousRunInfoBelowApi30Store.writeState(PreviousRunInfoBelowApi30State.JvmCrash)
    }

    private fun isBelowApi30() = !buildVersionChecker.isAtLeast(Build.VERSION_CODES.R)

    private fun getFromLegacyState(): PreviousRunInfo? {
        val previousState = previousRunInfoBelowApi30State ?: return null

        return when (previousState) {
            PreviousRunInfoBelowApi30State.JvmCrash ->
                PreviousRunInfo(
                    hasFatallyTerminated = true,
                    terminationReason = ExitReason.JvmCrash,
                )

            PreviousRunInfoBelowApi30State.Started -> PreviousRunInfo(hasFatallyTerminated = false)
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

@VisibleForTesting
internal class PreviousRunInfoBelowApi30Store(
    private val preferences: IPreferences,
) {
    fun getPreviousState(): PreviousRunInfoBelowApi30State? =
        preferences.getString(STATE_KEY)?.let(PreviousRunInfoBelowApi30State::fromName)

    fun writeState(state: PreviousRunInfoBelowApi30State) {
        val blocking = state == PreviousRunInfoBelowApi30State.JvmCrash
        preferences.setString(STATE_KEY, state.name, blocking = blocking)
    }

    private companion object {
        private const val STATE_KEY = "io.bitdrift.capture.previous_run_info.state"
    }
}

@VisibleForTesting
internal enum class PreviousRunInfoBelowApi30State {
    Started,
    JvmCrash,
    ;

    companion object {
        fun fromName(value: String): PreviousRunInfoBelowApi30State? = runCatching { valueOf(value) }.getOrNull()
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
