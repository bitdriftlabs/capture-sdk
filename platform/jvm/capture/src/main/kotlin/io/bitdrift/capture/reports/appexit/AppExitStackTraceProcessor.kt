// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.appexit

import io.bitdrift.capture.reports.FatalIssueType
import java.io.InputStream

/**
 * InputStream processor from [ApplicationExitReason]
 */
interface AppExitStackTraceProcessor {
    /**
     * Process a valid traceInputStream from [ApplicationExitReason]
     */
    fun process(traceInputStream: InputStream): ProcessedResult
}

/**
 * Process results types
 */
sealed class ProcessedResult {
    /**
     * Indicates a successful processing of [ApplicationExitInfo.traceInputStream].
     *
     * @property traceContents The valid contents of the processed ANR trace.
     * @property fatalIssueType The Fatal Issue type where the processing occurred
     */
    data class Success(
        val traceContents: String,
        val fatalIssueType: FatalIssueType,
    ) : ProcessedResult()

    /**
     * Indicates a failure in processing [ApplicationExitInfo.traceInputStream].
     *
     * @property errorDetails Details about the error encountered during processing.
     */
    data class Failed(
        val errorDetails: String,
    ) : ProcessedResult()
}
