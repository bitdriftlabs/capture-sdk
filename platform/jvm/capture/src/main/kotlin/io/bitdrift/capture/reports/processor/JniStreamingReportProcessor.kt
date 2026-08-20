// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.reports.processor

import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.LoggerId
import io.bitdrift.capture.attributes.IClientAttributes

/** Binds JNI report persistence to the logger and dynamic client metadata used by every report. */
internal class JniStreamingReportProcessor(
    private val loggerId: LoggerId,
    private val attributes: IClientAttributes,
) : IStreamingReportProcessor {
    override fun processAndPersistANR(report: AnrReport) {
        CaptureJniLibrary.processAndPersistANR(
            loggerId,
            report.stream,
            report.timestampMillis,
            report.destinationPath,
            attributes,
            report.runningState,
            report.appExitDescription,
            report.memoryPressureLevel,
            report.isFileSizeOptimizationEnabled,
        )
    }

    override fun processAndPersistJavaScriptError(report: JavaScriptErrorReport) {
        CaptureJniLibrary.processAndPersistJavaScriptError(
            loggerId,
            report.errorName,
            report.errorMessage,
            report.stackTrace,
            report.isFatal,
            report.engine,
            report.debugId,
            report.timestampMillis,
            report.destinationPath,
            attributes,
            report.sdkVersion,
        )
    }
}
