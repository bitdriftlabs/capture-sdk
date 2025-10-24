// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ActivityManager
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor

/**
 * Handles internal reporting of Fatal Issues
 */
interface IFatalIssueReporter {
    /**
     * Initializes the FatalIssueReporter
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
    fun initializationState(): FatalIssueReporterState

    /**
     * Generates the [InternalFieldsMap] to be reported upon Capture.Logger.start with
     * details of FatalIssueReporter state
     */
    fun getLogStatusFieldsMap(): Map<String, FieldValue>

    /**
     * Docs
     */
    fun persistJvmError(rawValue: String)
}
