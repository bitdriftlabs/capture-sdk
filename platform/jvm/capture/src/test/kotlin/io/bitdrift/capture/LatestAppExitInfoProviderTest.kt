// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProviderProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class LatestAppExitInfoProviderTest {
    private val activityManager: ActivityManager = mock()
    private lateinit var latestAppExitInfoProvider: LatestAppExitInfoProviderProvider

    @Before
    fun setUp() {
        latestAppExitInfoProvider =
            LatestAppExitInfoProviderProvider()
    }

    @Test
    fun get_withExceptionThrow_shouldReturnNullAndSendError() {
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenThrow(error)

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.Error).isTrue()
    }

    @Test
    fun get_withValidExitInfo_shouldNotReturnNull() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.Valid).isTrue()
    }

    @Test
    fun get_withEmptyExitReason_shouldReturnNull() {
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(emptyList())

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.Empty).isTrue()
    }
}
