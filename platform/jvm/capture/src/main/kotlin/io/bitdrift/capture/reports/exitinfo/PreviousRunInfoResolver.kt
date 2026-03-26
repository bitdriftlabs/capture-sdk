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

/**
 * Provides [PreviousRunInfo] for the previous app session.
 *
 * On API >= 30, uses [ApplicationExitInfo] directly.
 * On API < 30, returns `null` (no previous run info available yet). Will be implemented in BIT-7703.
 */
internal class PreviousRunInfoResolver(
    private val activityManager: ActivityManager,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
) : IPreviousRunInfoResolver {

    override fun get(): PreviousRunInfo? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // TODO (BIT-7703): Enable support below OS 11
            null
        } else {
            getFromAppExitInfo(activityManager)
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getFromAppExitInfo(activityManager: ActivityManager): PreviousRunInfo? =
        when (val result = latestAppExitInfoProvider.get(activityManager)) {
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

/**
 * Contract for producing [PreviousRunInfo] from app exit signals.
 */
internal fun interface IPreviousRunInfoResolver {
    /**
     * Returns previous run status, or `null` when previous run info is not available.
     */
    fun get(): PreviousRunInfo?
}
