// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build

/**
 * Retrieves the latest [ApplicationExitInfo] if available
 */
fun interface ILatestAppExitInfoProvider {
    /**
     * Returns the latest [ApplicationExitInfo] when present
     */
    @TargetApi(Build.VERSION_CODES.R)
    fun get(activityManager: ActivityManager): LatestAppExitReasonResult
}

/**
 * The [ApplicationExitInfo] result
 */
sealed class LatestAppExitReasonResult {
    /**
     * Returns the latest [ApplicationExitInfo] when available
     *
     * @param applicationExitInfo
     */
    data class Valid(
        val applicationExitInfo: ApplicationExitInfo,
    ) : LatestAppExitReasonResult()

    /**
     * Returns when there are no prior [ApplicationExitInfo]. i.e. initial app installation
     */
    data object Empty : LatestAppExitReasonResult()

    /**
     * Returns the detailed error while trying to determine prior reasons
     * @param message
     * @param throwable
     */
    data class Error(
        val message: String,
        val throwable: Throwable,
    ) : LatestAppExitReasonResult()
}
