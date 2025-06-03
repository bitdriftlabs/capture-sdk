// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.exitinfo

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import io.bitdrift.capture.reports.binformat.v1.ReportType

/**
 * Concrete impl of [ILatestAppExitInfoProvider]
 */
internal object LatestAppExitInfoProvider : ILatestAppExitInfoProvider {
    @TargetApi(Build.VERSION_CODES.R)
    override fun get(activityManager: ActivityManager): LatestAppExitReasonResult {
        try {
            // a null packageName means match all packages belonging to the caller's process (UID)
            // pid should be 0, a value of 0 means to ignore this parameter and return all matching records
            // maxNum should be 0, this will return the list of all last exists at the time
            val latestKnownExitReasons =
                activityManager
                    .getHistoricalProcessExitReasons(null, 0, 0)
            val matchingProcessReason =
                latestKnownExitReasons
                    .firstOrNull {
                        it.processName == Application.getProcessName()
                    }
            return if (latestKnownExitReasons.isEmpty()) {
                LatestAppExitReasonResult.Empty
            } else if (matchingProcessReason == null) {
                LatestAppExitReasonResult.ProcessNameNotFound
            } else {
                LatestAppExitReasonResult.Valid(matchingProcessReason)
            }
        } catch (error: Throwable) {
            return LatestAppExitReasonResult.Error(
                "Failed to retrieve ProcessExitReasons from ActivityManager",
                error,
            )
        }
    }

    fun mapToFatalIssueType(exitReasonType: Int): Byte? =
        when (exitReasonType) {
            ApplicationExitInfo.REASON_ANR -> ReportType.AppNotResponding
            ApplicationExitInfo.REASON_CRASH_NATIVE -> ReportType.NativeCrash
            else -> null
        }
}
