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
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType

/**
 * Concrete impl of [ILatestAppExitInfoProvider]
 */
internal object LatestAppExitInfoProvider : ILatestAppExitInfoProvider {
    internal const val EXIT_REASON_EMPTY_LIST_MESSAGE =
        "LatestAppExitInfoProvider: getHistoricalProcessExitReasons returned an empty list"
    internal const val EXIT_REASON_UNMATCHED_PROCESS_NAME_MESSAGE =
        "LatestAppExitInfoProvider: No matching process found in getHistoricalProcessExitReasons"
    internal const val EXIT_REASON_EXCEPTION_MESSAGE =
        "LatestAppExitInfoProvider: Failed to retrieve LatestAppExitReasonResult"

    @RequiresApi(Build.VERSION_CODES.R)
    override fun get(activityManager: ActivityManager): LatestAppExitReasonResult =
        try {
            // a null packageName means match all packages belonging to the caller's process (UID)
            // pid should be 0, a value of 0 means to ignore this parameter and return all matching records
            // maxNum should be 0, this will return the list of all last exists at the time
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

    fun mapToFatalIssueType(exitReasonType: Int): Byte? =
        when (exitReasonType) {
            ApplicationExitInfo.REASON_ANR -> ReportType.AppNotResponding
            ApplicationExitInfo.REASON_CRASH_NATIVE -> ReportType.NativeCrash
            else -> null
        }
}
