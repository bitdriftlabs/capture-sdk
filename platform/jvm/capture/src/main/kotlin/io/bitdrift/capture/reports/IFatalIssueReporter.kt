// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.app.ApplicationExitInfo
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.providers.FieldValue

/**
 * Sends [FatalIssueType] reports
 */
interface IFatalIssueReporter {
    /**
     * Init the reporter. This call should happen prior to Capture.Logger.start()
     */
    fun init()

    /**
     * The custom [FieldValue] to indicate status of this
     */
    fun getFatalIssueFieldMap(): Map<String, FieldValue>

    /**
     * Will process a valid [ApplicationExitInfo.getTraceInputStream] and store the processed trace
     */
    fun processAndStoreAppExitInfoTrace(
        errorHandler: ErrorHandler,
        applicationExitReason: ApplicationExitInfo,
    )
}
