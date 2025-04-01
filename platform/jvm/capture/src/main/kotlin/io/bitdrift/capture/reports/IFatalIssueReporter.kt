// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.providers.FieldValue
import java.io.InputStream

/**
 * Handles internal reporting of crashes
 */
interface IFatalIssueReporter {
    /**
     * TBF
     */
    fun getLogStatusFieldsMap(): Map<String, FieldValue>

    /**
     * FetchInitialization status
     */
    fun fetchStatus(): FatalIssueReporterStatus

    /**
     * Initializes the reporter
     */
    fun initialize(fatalIssueMechanism: FatalIssueMechanism)

    /**
     * Persists into disk when [FatalIssueMechanism.BUILT_IN] is configured
     */
    fun persistJvmCrash(
        errorHandler: ErrorHandler,
        timestamp: Long,
        callerThread: Thread,
        throwable: Throwable,
    )

    /**
     * Persists into disk when [FatalIssueMechanism.BUILT_IN] is configured
     */
    fun persistAppExitReport(
        errorHandler: ErrorHandler,
        timestamp: Long,
        exitReasonType: Int,
        traceInputStream: InputStream,
    )
}
