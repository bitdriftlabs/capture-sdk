// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

/**
 * Process existing reports via JNI layer
 */
interface ICompletedReportsProcessor {
    /**
     * To be called when we are ready to process existing fatal issue reports stored on disk
     */
    fun processCrashReports()

    /**
     * Will be called if there is an issue while processing reports
     */
    fun onReportProcessingError(
        message: String,
        throwable: Throwable,
    )
}
