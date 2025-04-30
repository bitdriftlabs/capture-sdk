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
import io.bitdrift.capture.reports.ThreadEntry

/**
 * Process crash into a List<io.bitdrift.capture.reports.ErrorDetails> and
 *  io.bitdrift.capture.reports.ThreadDetails
 */
internal object JvmCrashProcessor {
    private const val CLASS_NAME_SEPARATOR = "."

    fun getJvmCrashReport(
        sdk: Sdk,
        appMetrics: AppMetrics,
        deviceMetrics: DeviceMetrics,
        throwable: Throwable,
        callerThread: Thread,
        allThreads: Map<Thread, Array<StackTraceElement>>?,
    ): FatalIssueReport {
        val frameDetails: List<FrameDetails> =
            throwable.stackTrace.map { e -> getFrameDetails(e) }
        val errors =
            listOf(
                ErrorDetails(
                    name = throwable.javaClass.name,
                    reason = throwable.message,
                    stackTrace = frameDetails,
                ),
            )

        val threadList =
            allThreads?.map { (thread, frames) ->
                ThreadEntry(
                    name = thread.name,
                    active = (thread == callerThread),
                    index = thread.id,
                    state = thread.state.name,
                    stackTrace = frames.map { e -> getFrameDetails(e) },
                )
            } ?: listOf()
        return FatalIssueReport(
            sdk,
            appMetrics,
            deviceMetrics,
            errors,
            ThreadDetails(threadList.size, threadList),
        )
    }

    private fun getMethodName(stackTraceElement: StackTraceElement): String {
        val className = stackTraceElement.className
        return when {
            stackTraceElement.className.isNotEmpty() -> className + CLASS_NAME_SEPARATOR + stackTraceElement.methodName
            else -> stackTraceElement.methodName
        }
    }

    private fun getFrameDetails(element: StackTraceElement): FrameDetails =
        FrameDetails(
            type = FrameType.JVM.ordinal,
            className = element.className,
            symbolName = getMethodName(element),
            sourceFile =
                SourceFile(
                    path = element.fileName,
                    lineNumber = element.lineNumber,
                ),
        )
}
