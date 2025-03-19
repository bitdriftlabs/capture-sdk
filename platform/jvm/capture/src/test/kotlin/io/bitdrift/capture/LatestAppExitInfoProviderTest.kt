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
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProviderProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class LatestAppExitInfoProviderTest {
    private val activityManager: ActivityManager = mock()

    private val errorHandler: ErrorHandler = mock()

    private lateinit var latestAppExitInfoProvider: LatestAppExitInfoProviderProvider

    @Before
    fun setUp() {
        latestAppExitInfoProvider =
            LatestAppExitInfoProviderProvider(
                activityManager,
                errorHandler,
            )
    }

    @Test
    fun get_withExceptionThrow_shouldReturnNullAndSendError() {
        // ARRANGE
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenThrow(error)

        // ACT
        val exitReason = latestAppExitInfoProvider.get()

        // ASSERT
        verify(errorHandler).handleError(any(), eq(error))
        assertThat(exitReason).isNull()
    }

    @Test
    fun get_withValidExitInfo_shouldNotReturnNull() {
        // ARRANGE
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        // ACT
        val exitReason = latestAppExitInfoProvider.get()

        // ASSERT
        assertThat(exitReason).isNotNull()
        verify(errorHandler, never()).handleError(any(), any())
    }

    @Test
    fun get_withEmptyExitReason_shouldReturnNull() {
        // ARRANGE
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(emptyList())

        // ACT
        val exitReason = latestAppExitInfoProvider.get()

        // ASSERT
        assertThat(exitReason).isNull()
    }
}
