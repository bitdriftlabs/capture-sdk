// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import io.bitdrift.capture.reports.AppMetrics
import io.bitdrift.capture.reports.DeviceMetrics
import io.bitdrift.capture.reports.ErrorDetails
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FrameDetails
import io.bitdrift.capture.reports.FrameType
import io.bitdrift.capture.reports.Sdk
import io.bitdrift.capture.reports.SourceFile
import io.bitdrift.capture.reports.ThreadDetails
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor.Companion.UNKNOWN_FIELD_VALUE
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Process an converts an ANR report from [android.app.ApplicationExitInfo]
 * into a io.bitdrift.capture.reports.FatalIssueReport
 */
internal object AppExitAnrTraceProcessor {
    private const val ANR_MAIN_THREAD_IDENTIFIED = "\"main\""
    private const val ANR_STACKTRACE_PREFIX = "at "
    private val ANR_STACK_TRACE_REGEX = Regex("^\\s+at\\s+(.*)\\.(.*)\\((.*):(\\d+)\\)$")
    private val mainStackTraceFrames = mutableListOf<FrameDetails>()
    private var isProcessingMainThreadTrace = false
    private var mainThreadReason = UNKNOWN_FIELD_VALUE

    /**
     * Process valid traceInputStream
     */
    fun process(
        sdk: Sdk,
        appMetrics: AppMetrics,
        deviceMetrics: DeviceMetrics,
        description: String?,
        traceInputStream: InputStream,
    ): FatalIssueReport {
        val inputStreamReader = InputStreamReader(traceInputStream)
        BufferedReader(inputStreamReader)
            .useLines { lines ->
                lines.forEach { currentLine ->
                    appendMainFramesIfNeeded(currentLine, appMetrics.appId)
                }
            }
        val errors =
            listOf(
                ErrorDetails(
                    name = mainThreadReason,
                    // TODO(Fran): BIT-5143 To polish reason
                    reason = description ?: UNKNOWN_FIELD_VALUE,
                    stackTrace = mainStackTraceFrames,
                ),
            )
        return FatalIssueReport(
            sdk,
            appMetrics,
            deviceMetrics,
            errors,
            // TODO(FranAguilera): BIT-5142. Append thread info
            ThreadDetails(),
        )
    }

    private fun isStackTraceLine(currentLine: String) = currentLine.trim().startsWith(ANR_STACKTRACE_PREFIX)

    private fun setIsMainThreadStackTrace(line: String) {
        if (line.startsWith(ANR_MAIN_THREAD_IDENTIFIED)) {
            isProcessingMainThreadTrace = true
        } else if (isProcessingMainThreadTrace && line.isEmpty()) {
            isProcessingMainThreadTrace = false
        }
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    private fun appendMainFramesIfNeeded(
        currentLine: String,
        applicationId: String,
    ) {
        setIsMainThreadStackTrace(currentLine)
        if (!isStackTraceLine(currentLine)) return

        ANR_STACK_TRACE_REGEX.find(currentLine)?.destructured?.let { (className, symbolName, fileName, lineNumber) ->
            val sourceFile =
                SourceFile(
                    path = fileName,
                    lineNumber = lineNumber.toIntOrNull() ?: -1,
                )
            val frame =
                FrameDetails(
                    type = FrameType.JVM.ordinal,
                    className = className,
                    symbolName = "$className.$symbolName",
                    sourceFile = sourceFile,
                )
            if (isProcessingMainThreadTrace) {
                mainStackTraceFrames.add(frame)
            }
        }
        setAnrReason(currentLine, applicationId)
    }

    private fun setAnrReason(
        currentLine: String,
        applicationId: String,
    ) {
        if (!isProcessingMainThreadTrace) return

        val matchesAppId = currentLine.trim().contains(applicationId.trim())
        if (mainThreadReason == UNKNOWN_FIELD_VALUE && matchesAppId) {
            mainThreadReason = currentLine.trim()
        }
    }
}
