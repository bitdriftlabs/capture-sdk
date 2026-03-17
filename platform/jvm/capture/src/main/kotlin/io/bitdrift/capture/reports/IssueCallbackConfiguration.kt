// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

import android.util.Log
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.IInternalLogger
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import kotlin.jvm.JvmName

/**
 * Configuration for issue report callbacks.
 *
 * Use this to provide:
 * - the executor where callbacks run
 * - the callback that receives issue report metadata before send
 *
 * @param executor Executor used to run callback invocations.
 * @param issueReportCallback Callback invoked before an issue report is sent.
 */
class IssueCallbackConfiguration(
    private val executor: Executor,
    private val issueReportCallback: IssueReportCallback,
) {
    /**
     * Dispatches the callback on the configured executor from the JNI layer
     */
    @Suppress("unused")
    @JvmName("dispatch")
    internal fun dispatch(report: Report): Boolean {
        val task = FutureTask { invokeCallback(report) }
        return runCatching {
            executor.execute(task)
            task.get()
        }.onFailure {
            handleFailure("Issue report callback dispatch failed", it)
        }.getOrDefault(true)
    }

    private fun invokeCallback(report: Report): Boolean {
        var shouldSend = true
        runSafely("Issue report callback failed") {
            shouldSend = issueReportCallback.onBeforeReportSend(report)
        }
        return shouldSend
    }

    private inline fun runSafely(
        message: String,
        action: () -> Unit,
    ) {
        runCatching(action).onFailure { handleFailure(message, it) }
    }

    private fun handleFailure(
        message: String,
        throwable: Throwable,
    ) {
        Log.e(LOG_TAG, message, throwable)
        getInternalLogger()?.logInternalError(throwable) { message }
    }

    private fun getInternalLogger(): IInternalLogger? = Capture.logger() as? IInternalLogger
}

/**
 * Issue report metadata delivered to [IssueReportCallback] before send.
 *
 * @param reportType High-level issue type (for example "ANR", "Native Crash", "Crash").
 * @param reason Primary issue identifier (for example exception class or signal name).
 * @param details Additional issue details (for example exception message).
 * @param sessionId bitdrift session ID associated with the report.
 * @param fields Additional report fields available at callback time.
 */
data class Report(
    val reportType: String,
    val reason: String,
    val details: String,
    val sessionId: String,
    val fields: Map<String, String>,
)

/**
 * Callback invoked before an issue report (crash, ANR, etc.) is sent.
 */
fun interface IssueReportCallback {
    /**
     * Called before an issue report is sent.
     *
     * Return `true` to continue report processing.
     * Return `false` to drop the report.
     */
    fun onBeforeReportSend(report: Report): Boolean
}
