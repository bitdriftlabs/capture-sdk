// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture.reports.exitinfo

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.Companion.EXIT_REASON_EXCEPTION_MESSAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LatestAppExitInfoProviderTest {
    private val activityManager: ActivityManager = mock()

    @Test
    fun get_withExceptionThrow_shouldReturnNullAndSendError() {
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenThrow(error)

        val latestAppExitInfoProvider = LatestAppExitInfoProvider(activityManager)

        val exitReason = latestAppExitInfoProvider.get()

        assertResult<LatestAppExitReasonResult.Error>(
            exitReason,
            EXIT_REASON_EXCEPTION_MESSAGE,
        )
    }

    @Test
    fun get_withValidExitInfo_shouldNotReturnNull() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(mockExitInfo.processName).thenReturn(Application.getProcessName())
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        val latestAppExitInfoProvider = LatestAppExitInfoProvider(activityManager)

        val exitReason = latestAppExitInfoProvider.get()

        assertResult<LatestAppExitReasonResult.Valid>(
            exitReason,
            expectedApplicationExitInfo = mockExitInfo,
        )
    }

    @Test
    fun get_withEmptyExitReason_shouldReturnEmpty() {
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(emptyList())

        val latestAppExitInfoProvider = LatestAppExitInfoProvider(activityManager)

        val exitReason = latestAppExitInfoProvider.get()

        assertResult<LatestAppExitReasonResult.None>(
            exitReason,
        )
    }

    @Test
    fun get_withUnmatchedProcessName_shouldReturnProcessNameNotFound() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(listOf(mockExitInfo))

        val latestAppExitInfoProvider = LatestAppExitInfoProvider(activityManager)

        val exitReason = latestAppExitInfoProvider.get()

        assertResult<LatestAppExitReasonResult.None>(
            exitReason,
        )
    }

    @Test
    fun get_multipleCalls_shouldOnlyQueryActivityManagerOnce() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(mockExitInfo.processName).thenReturn(Application.getProcessName())
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        val latestAppExitInfoProvider = LatestAppExitInfoProvider(activityManager)

        val initialResult = latestAppExitInfoProvider.get()
        latestAppExitInfoProvider.get()
        latestAppExitInfoProvider.get()
        val latestResult = latestAppExitInfoProvider.get()

        assertThat(initialResult).isEqualTo(latestResult)
        verify(activityManager, times(1)).getHistoricalProcessExitReasons(anyOrNull(), any(), any())
    }

    /**
     * BIT-10458: a normal reboot kills the app without adding an exit record, so on the next process
     * start getHistoricalProcessExitReasons() still returns the native crash that was already reported.
     * Each provider instance stands for one process start.
     */
    @Test
    fun get_whenSameExitRecordOnNextProcessStart_shouldNotReportItAgain() {
        val nativeCrash = mockExitInfo(pid = 3544, timestamp = 1_793_974_836_144L, reason = ApplicationExitInfo.REASON_CRASH_NATIVE)
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(nativeCrash))

        // Start right after the crash: the crash is reported.
        val startAfterCrash = LatestAppExitInfoProvider(activityManager).get()
        // Start after a reboot: Android still returns the same record, as nothing newer was recorded.
        val startAfterReboot = LatestAppExitInfoProvider(activityManager).get()

        assertResult<LatestAppExitReasonResult.Valid>(startAfterCrash, expectedApplicationExitInfo = nativeCrash)
        assertResult<LatestAppExitReasonResult.None>(startAfterReboot)
    }

    @Test
    fun get_whenNewerExitRecordOnNextProcessStart_shouldReportTheNewerRecord() {
        val nativeCrash = mockExitInfo(pid = 3544, timestamp = 1_793_974_836_144L, reason = ApplicationExitInfo.REASON_CRASH_NATIVE)
        val laterExit = mockExitInfo(pid = 3717, timestamp = 1_793_976_000_000L, reason = ApplicationExitInfo.REASON_USER_REQUESTED)
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any()))
            .thenReturn(listOf(nativeCrash))
            .thenReturn(listOf(laterExit, nativeCrash))

        val startAfterCrash = LatestAppExitInfoProvider(activityManager).get()
        val nextStart = LatestAppExitInfoProvider(activityManager).get()

        assertResult<LatestAppExitReasonResult.Valid>(startAfterCrash, expectedApplicationExitInfo = nativeCrash)
        assertResult<LatestAppExitReasonResult.Valid>(nextStart, expectedApplicationExitInfo = laterExit)
    }

    private fun mockExitInfo(
        pid: Int,
        timestamp: Long,
        reason: Int,
    ): ApplicationExitInfo =
        mock<ApplicationExitInfo>().also {
            whenever(it.processName).thenReturn(Application.getProcessName())
            whenever(it.pid).thenReturn(pid)
            whenever(it.timestamp).thenReturn(timestamp)
            whenever(it.reason).thenReturn(reason)
        }

    private inline fun <reified T : LatestAppExitReasonResult> assertResult(
        exitReason: LatestAppExitReasonResult,
        expectedMessage: String? = null,
        expectedApplicationExitInfo: ApplicationExitInfo? = null,
    ) {
        assertThat(exitReason).isInstanceOf(T::class.java)

        when (exitReason) {
            is LatestAppExitReasonResult.Valid -> {
                assertThat(exitReason.applicationExitInfo).isEqualTo(expectedApplicationExitInfo)
            }
            is LatestAppExitReasonResult.Error -> {
                assertThat(exitReason.message).isEqualTo(expectedMessage)
            }
            is LatestAppExitReasonResult.None -> {
                assertThat(expectedMessage).isNull()
            }
        }
    }
}
