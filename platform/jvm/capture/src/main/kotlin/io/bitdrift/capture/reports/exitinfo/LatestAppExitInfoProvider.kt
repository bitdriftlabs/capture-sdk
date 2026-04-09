// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.exitinfo

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Concrete impl of [ILatestAppExitInfoProvider]
 */
internal class LatestAppExitInfoProvider(
    private val activityManager: ActivityManager,
) : ILatestAppExitInfoProvider {
    /**
     * Caching after initial fetch to avoid unnecessary IPC binder calls once value is retrieved
     */
    private val cachedResult: LatestAppExitReasonResult by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            LatestAppExitReasonResult.None
        } else {
            getReason()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun get(): LatestAppExitReasonResult = cachedResult

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getReason(): LatestAppExitReasonResult =
        try {
            // a null packageName means match all packages belonging to the caller's process (UID)
            // pid should be 0, a value of 0 means to ignore this parameter and return all matching records
            // maxNum should be 0, this will return the list of all last exits at the time
            val latestKnownExitReasons =
                activityManager
                    .getHistoricalProcessExitReasons(null, 0, 0)

            val matchingProcessReason =
                latestKnownExitReasons.firstOrNull {
                    it.processName == Application.getProcessName()
                }

            if (matchingProcessReason == null) {
                LatestAppExitReasonResult.None
            } else {
                LatestAppExitReasonResult.Valid(matchingProcessReason)
            }
        } catch (error: Throwable) {
            LatestAppExitReasonResult.Error(
                EXIT_REASON_EXCEPTION_MESSAGE,
                error,
            )
        }

    internal companion object {
        internal const val EXIT_REASON_EXCEPTION_MESSAGE =
            "LatestAppExitInfoProvider: Failed to retrieve LatestAppExitReasonResult"
    }
}

/**
 * Retrieves the latest [ApplicationExitInfo] if available.
 */
interface ILatestAppExitInfoProvider {
    /**
     * Returns the latest [ApplicationExitInfo] when present.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun get(): LatestAppExitReasonResult
}

/**
 * The [ApplicationExitInfo] lookup result.
 */
sealed class LatestAppExitReasonResult {
    /**
     * Returns the latest [ApplicationExitInfo] when available.
     */
    data class Valid(
        /** Latest [ApplicationExitInfo] for the current process. */
        val applicationExitInfo: ApplicationExitInfo,
    ) : LatestAppExitReasonResult()

    /**
     * No [ApplicationExitInfo] was available.
     * (e.g. this is expected on first app installation)
     */
    data object None : LatestAppExitReasonResult()

    /**
     * Returns the detailed error while trying to determine prior reasons.
     */
    data class Error(
        /** Human-readable message describing the lookup failure. */
        val message: String,
        /** Optional throwable associated with the lookup failure. */
        val throwable: Throwable? = null,
    ) : LatestAppExitReasonResult()
}
