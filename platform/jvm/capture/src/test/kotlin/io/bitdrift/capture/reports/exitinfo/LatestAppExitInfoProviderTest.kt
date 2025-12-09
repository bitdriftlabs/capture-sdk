// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_EMPTY_LIST_MESSAGE
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_EXCEPTION_MESSAGE
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider.EXIT_REASON_UNMATCHED_PROCESS_NAME_MESSAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LatestAppExitInfoProviderTest {
    private val activityManager: ActivityManager = mock()
    private val latestAppExitInfoProvider = LatestAppExitInfoProvider

    @Test
    fun get_withExceptionThrow_shouldReturnNullAndSendError() {
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenThrow(error)

        val exitReason = latestAppExitInfoProvider.get(activityManager)

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

        val exitReason = latestAppExitInfoProvider.get(activityManager)

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

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertResult<LatestAppExitReasonResult.Error>(
            exitReason,
            EXIT_REASON_EMPTY_LIST_MESSAGE,
        )
    }

    @Test
    fun get_withUnmatchedProcessName_shouldReturnProcessNameNotFound() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(listOf(mockExitInfo))

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertResult<LatestAppExitReasonResult.Error>(
            exitReason,
            EXIT_REASON_UNMATCHED_PROCESS_NAME_MESSAGE,
        )
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
