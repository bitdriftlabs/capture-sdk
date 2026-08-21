// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

import java.io.InputStream

/** Immutable input for persisting an ANR report. */
internal data class AnrReport(
    val stream: InputStream?,
    val timestampMillis: Long,
    val destinationPath: String,
    val runningState: String?,
    val appExitDescription: String?,
    val memoryPressureLevel: Int,
    val isFileSizeOptimizationEnabled: Boolean,
)

/** Immutable input for persisting a JavaScript error report. */
internal data class JavaScriptErrorReport(
    val errorName: String,
    val errorMessage: String,
    val stackTrace: String,
    val isFatal: Boolean,
    val engine: String,
    val debugId: String,
    val timestampMillis: Long,
    val destinationPath: String,
    val sdkVersion: String,
)

/**
 * Process reports via streaming values
 */
internal interface IStreamingReportProcessor {
    /**
     * Call to convert a trace input stream into a report file
     */
    fun processAndPersistANR(report: AnrReport)

    /**
     * Call to convert a JS error trace into a fbs report file
     */
    fun processAndPersistJavaScriptError(report: JavaScriptErrorReport)
}
