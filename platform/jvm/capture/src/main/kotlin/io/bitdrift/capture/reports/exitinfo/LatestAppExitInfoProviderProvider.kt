// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.exitinfo

import android.annotation.TargetApi
import android.app.ActivityManager
import android.os.Build

/**
 * Concrete impl of [ILatestAppExitInfoProvider]
 */
internal class LatestAppExitInfoProviderProvider : ILatestAppExitInfoProvider {
    @TargetApi(Build.VERSION_CODES.R)
    override fun get(activityManager: ActivityManager): LatestAppExitReasonResult {
        try {
            // a null packageName means match all packages belonging to the caller's process (UID)
            // pid should be 0, a value of 0 means to ignore this parameter and return all matching records
            // maxNum should be 1, The maximum number of results to be returned, as we need only the last one
            val latestExitReasons = activityManager.getHistoricalProcessExitReasons(null, 0, 1)
            return if (latestExitReasons.isEmpty()) {
                LatestAppExitReasonResult.Empty
            } else {
                LatestAppExitReasonResult.Valid(latestExitReasons.first())
            }
        } catch (error: Throwable) {
            return LatestAppExitReasonResult.Error(
                "Failed to retrieve ProcessExitReasons from ActivityManager",
                error,
            )
        }
    }
}
