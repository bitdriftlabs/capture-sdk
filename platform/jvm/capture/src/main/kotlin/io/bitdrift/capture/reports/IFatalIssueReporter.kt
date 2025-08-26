// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.content.Context
import io.bitdrift.capture.attributes.IClientAttributes
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.reports.processor.ICompletedReportsProcessor

/**
 * Handles internal reporting of Fatal Issues
 */
interface IFatalIssueReporter {
    /**
     * Initializes the BuiltIn reporter
     */
    fun initBuiltInMode(
        appContext: Context,
        clientAttributes: IClientAttributes,
        completedReportsProcessor: ICompletedReportsProcessor,
    )

    /**
     * Returns the configured [io.bitdrift.capture.reports.FatalIssueMechanism]
     */
    fun getReportingMechanism(): FatalIssueMechanism

    /**
     * Generates the [InternalFieldsMap] to be reported upon Capture.Logger.start with
     * details of FatalIssueReporter state
     */
    fun getLogStatusFieldsMap(): Map<String, FieldValue>
}
