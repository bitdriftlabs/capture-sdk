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
     * A traceInputStrem for REASON_ANR or REASON_CRASH_NATIVE
     */
    private var traceInputStream: InputStream? = null

    /**
     * To set, if there are no prior exit reason
     */
    private var hasNoPriorReason: Boolean = false

    /**
     * Additional description fields for the app termination
     */
    private var description: String = DEFAULT_DESCRIPTION

    /*
     * Sets to default state
     */
    fun reset() {
        exitReasonType = ApplicationExitInfo.REASON_UNKNOWN
        traceInputStream = null
        hasNoPriorReason = false
        description = DEFAULT_DESCRIPTION
    }

    /**
     * Set different [ApplicationExitInfo] properties
     */
    fun set(
        exitReasonType: Int,
        traceInputStream: InputStream? = null,
        hasNoPriorReason: Boolean = false,
        description: String = DEFAULT_DESCRIPTION,
    ) {
        this.exitReasonType = exitReasonType
        this.traceInputStream = traceInputStream
        this.hasNoPriorReason = hasNoPriorReason
        this.description = description
    }

    override fun get(activityManager: ActivityManager): LatestAppExitReasonResult {
        if (hasNoPriorReason) return LatestAppExitReasonResult.Empty

        val appExitReason = mock<ApplicationExitInfo>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(appExitReason.processStateSummary).thenReturn(
            SESSION_ID.toByteArray(
                StandardCharsets.UTF_8,
            ),
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
        const val DEFAULT_DESCRIPTION = "test-description"
    }
}
