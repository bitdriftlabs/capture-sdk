// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * A lightweight fake to return different [ApplicationExitInfo]
 */
class FakeLatestAppExitInfoProvider : ILatestAppExitInfoProvider {
    /**
     * The last known reason for app termination (e.g. REASON_USER_REQUESTED)
     */
    private var exitReasonType: Int = ApplicationExitInfo.REASON_UNKNOWN

    /**
     * A traceInputStream for REASON_ANR or REASON_CRASH_NATIVE
     */
    private var traceInputStream: InputStream? = null

    /**
     * To set, if there are no prior exit reason
     */
    private var hasNoPriorReason: Boolean = false

    /**
     * To set, if there are errors while retrieving reason
     */
    private var hasErrorResult: Boolean = false

    /**
     * To set, if there is no matching process name on historical reasons
     */
    private var hasNotMatchedOnProcessName: Boolean = false

    /**
     * Additional description fields for the app termination
     */
    private var description: String = DEFAULT_DESCRIPTION

    /**
     * Specifies any prior process state summary
     */
    private var processStateSummary: ByteArray? = PROCESS_STATE_SUMMARY

    /**
     * Specifies the process name of each exit reason entry
     */
    private var processName: String = PROCESS_NAME

    /*
     * Sets to default state
     */
    fun reset() {
        exitReasonType = ApplicationExitInfo.REASON_UNKNOWN
        traceInputStream = null
        hasNoPriorReason = false
        hasErrorResult = false
        hasNotMatchedOnProcessName = false
        description = DEFAULT_DESCRIPTION
        processStateSummary = PROCESS_STATE_SUMMARY
    }

    /**
     * Set different [ApplicationExitInfo] properties
     */
    fun setAsValidReason(
        exitReasonType: Int,
        traceInputStream: InputStream? = null,
        description: String = DEFAULT_DESCRIPTION,
        processStateSummary: ByteArray? = PROCESS_STATE_SUMMARY,
    ) {
        this.exitReasonType = exitReasonType
        this.traceInputStream = traceInputStream
        this.description = description
        this.processStateSummary = processStateSummary
    }

    /**
     * Forces latestAppExitInfoProvider.get() to return [LatestAppExitReasonResult.Empty]
     */
    fun setAsEmptyReason() {
        hasNoPriorReason = true
    }

    /**
     * Forces latestAppExitInfoProvider.get() to return [LatestAppExitReasonResult.Error]
     */
    fun setAsErrorResult() {
        hasErrorResult = true
    }

    /**
     * Forces latestAppExitInfoProvider.get() to return [LatestAppExitReasonResult.ProcessNameNotFound]
     */
    fun setAsInvalidProcessName() {
        hasNotMatchedOnProcessName = true
    }

    override fun get(activityManager: ActivityManager): LatestAppExitReasonResult {
        if (hasNoPriorReason) {
            return LatestAppExitReasonResult.Error("LatestAppExitInfoProvider: getHistoricalProcessExitReasons returned an empty list")
        } else if (hasNotMatchedOnProcessName) {
            return LatestAppExitReasonResult.Error(
                "LatestAppExitInfoProvider: No matching process found in getHistoricalProcessExitReasons",
            )
        } else if (hasErrorResult) {
            return LatestAppExitReasonResult.Error(
                "LatestAppExitInfoProvider: Failed to retrieve LatestAppExitReasonResult",
                FAKE_EXCEPTION,
            )
        }

        val appExitReason = mock<ApplicationExitInfo>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(appExitReason.processStateSummary).thenReturn(
            processStateSummary,
        )
        whenever(appExitReason.timestamp).thenReturn(TIME_STAMP)
        whenever(appExitReason.processName).thenReturn("test-process-name")
        whenever(appExitReason.reason).thenReturn(exitReasonType)
        whenever(appExitReason.importance).thenReturn(RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        whenever(appExitReason.status).thenReturn(0)
        whenever(appExitReason.pss).thenReturn(1)
        whenever(appExitReason.rss).thenReturn(2)
        whenever(appExitReason.description).thenReturn(description)
        whenever(appExitReason.traceInputStream).thenReturn(traceInputStream)
        return LatestAppExitReasonResult.Valid(appExitReason)
    }

    companion object {
        const val SESSION_ID = "uuid-test-sample"
        const val TIME_STAMP = 1742376168992
        const val PROCESS_NAME = "test-process-name"
        val PROCESS_STATE_SUMMARY = SESSION_ID.toByteArray(StandardCharsets.UTF_8)
        const val DEFAULT_DESCRIPTION = "test-description"
        val FAKE_EXCEPTION by lazy {
            Exception(
                "failed: java.lang.IllegalArgumentException: " +
                    "Comparison method violates its general contract",
            )
        }

        fun createTraceInputStream(rawText: String): InputStream = ByteArrayInputStream(rawText.toByteArray(Charsets.UTF_8))
    }
}
