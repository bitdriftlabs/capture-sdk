// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ActivityManager
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor

/**
 * Handles internal reporting of Issues (JVM crash, ANR, native, StrictMode, etc)
 */
interface IIssueReporter {
    /**
     * Initializes the IssueReporter
     */
    fun init(
        activityManager: ActivityManager,
        sdkDirectory: String,
        clientAttributes: IClientAttributes,
        completedReportsProcessor: ICompletedReportsProcessor,
    )

    /**
     * Returns the current initialization state
     */
    fun initializationState(): IssueReporterState

    /**
     * Generates the fields to be reported upon Capture.Logger.start with
     * details of IssueReporterState
     */
    fun getLogStatusFieldsMap(): Map<String, String>
}
