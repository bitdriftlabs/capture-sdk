// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import io.bitdrift.capture.reports.Exception
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.FatalIssueType
import io.bitdrift.capture.reports.persistence.IFatalIssueReporterStorage
import java.io.InputStream

/**
 * Process reports from [BUILT_IN] mechanism into a [FatalIssueReport] format
 */
internal class FatalIssueReporterProcessor(
    private val fatalIssueReporterStorage: IFatalIssueReporterStorage,
) {
    /**
     * Process AppTerminations due to [REASON_ANR] or [REASON_CRASH_NATIVE] into [FatalIssueReport] format
     */
    fun persistAppExitReport(
        fatalIssueType: FatalIssueType,
        timestamp: Long,
        description: String? = null,
        traceInputStream: InputStream,
    ) {
        val report: FatalIssueReport? =
            when (fatalIssueType) {
                FatalIssueType.ANR -> {
                    getAnrReport(description, traceInputStream)
                }

                FatalIssueType.NATIVE_CRASH -> {
                    getNativeCrashReport(description, traceInputStream)
                }

                else -> null
            }
        report?.let {
            fatalIssueReporterStorage
                .persistFatalIssue(
                    timestamp,
                    fatalIssueType,
                    report,
                )
        }
    }

    /**
     * Process JVM crashes into a [FatalIssueReport] format
     *
     * NOTE: This will need to run by default on the caller thread
     */
    fun persistJvmCrash(
        timestamp: Long,
        callerThread: Thread,
        throwable: Throwable,
    ) {
        val fatalIssueReport = getJvmCrashReport(timestamp, callerThread, throwable)
        fatalIssueReporterStorage
            .persistFatalIssue(
                timestamp,
                FatalIssueType.JVM_CRASH,
                fatalIssueReport,
            )
    }

    /**
     * TODO(FranAguilera): BIT-5070 Update to include full FatalIssueReport
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getJvmCrashReport(
        timestamp: Long,
        thread: Thread,
        throwable: Throwable,
    ): FatalIssueReport {
        val errors =
            listOf(
                Exception(
                    name = throwable.javaClass.name,
                    reason = throwable.message ?: "n/a",
                ),
            )
        return FatalIssueReport(FatalIssueType.JVM_CRASH, errors)
    }

    /**
     * TODO(FranAguilera): BIT-5070 Update to include full FatalIssueReport
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getNativeCrashReport(
        description: String?,
        traceInputStream: InputStream,
    ): FatalIssueReport = FatalIssueReport(FatalIssueType.NATIVE_CRASH, emptyList())

    /**
     * TODO(FranAguilera): BIT-5070 Update to include full FatalIssueReport
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getAnrReport(
        description: String?,
        traceInputStream: InputStream,
    ): FatalIssueReport {
        val errors =
            listOf(
                Exception(
                    name = description ?: "n/a",
                    reason = "ANR reason wip",
                ),
            )
        return FatalIssueReport(FatalIssueType.ANR, errors)
    }
}
