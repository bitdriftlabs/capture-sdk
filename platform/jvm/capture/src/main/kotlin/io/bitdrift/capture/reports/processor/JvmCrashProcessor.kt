// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import io.bitdrift.capture.reports.ErrorDetails
import io.bitdrift.capture.reports.FrameDetails
import io.bitdrift.capture.reports.FrameType
import io.bitdrift.capture.reports.SourceFile
import io.bitdrift.capture.reports.ThreadDetails
import io.bitdrift.capture.reports.processor.FatalIssueReporterProcessor.Companion.UNKNOWN_FIELD_VALUE

/**
 * Process crash into a List<io.bitdrift.capture.reports.ErrorDetails> and
 *  io.bitdrift.capture.reports.ThreadDetails
 */
internal object JvmCrashProcessor {
    private const val CLASS_NAME_SEPARATOR = "."
    private const val INVALID_LINE_NUMBER_ID = -1

    fun getJvmCrashReport(throwable: Throwable): ProcessedData {
        val frameDetails: List<FrameDetails> =
            throwable.stackTrace.map { element ->
                val sourceFile =
                    SourceFile(
                        path = element.fileName ?: UNKNOWN_FIELD_VALUE,
                        lineNumber =
                            element.lineNumber.takeIf { it >= 0 }
                                ?: INVALID_LINE_NUMBER_ID,
                    )
                FrameDetails(
                    type = FrameType.JVM.ordinal,
                    className = element.className ?: UNKNOWN_FIELD_VALUE,
                    symbolName = getMethodName(element),
                    sourceFile = sourceFile,
                )
            }
        val errors =
            listOf(
                ErrorDetails(
                    name = throwable.javaClass.name,
                    reason = throwable.message ?: UNKNOWN_FIELD_VALUE,
                    stackTrace = frameDetails,
                ),
            )
        // TODO(FranAguilera): BIT-5142. Append thread info
        return ProcessedData(errors = errors, threadDetails = ThreadDetails())
    }

    private fun getMethodName(stackTraceElement: StackTraceElement): String {
        val className = stackTraceElement.className
        return when {
            stackTraceElement.className.isNotEmpty() -> className + CLASS_NAME_SEPARATOR + stackTraceElement.methodName
            else -> stackTraceElement.methodName
        }
    }
}
