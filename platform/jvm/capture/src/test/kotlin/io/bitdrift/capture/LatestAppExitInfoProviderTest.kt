// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test

// TODO(FranAguilera): BIT-5484. This works on gradle with Roboelectric. Fix on bazel
// @RunWith(RobolectricTestRunner::class)
// @Config(sdk = [30])
class LatestAppExitInfoProviderTest {
    private val activityManager: ActivityManager = mock()
    private val latestAppExitInfoProvider = LatestAppExitInfoProvider

    @Test
    fun get_withExceptionThrow_shouldReturnNullAndSendError() {
        val error = RuntimeException("test exception")
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenThrow(error)

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.Error).isTrue()
    }

    @Ignore("TODO(FranAguilera): BIT-5484 This works on gradle with Roboelectric. Fix on bazel")
    @Test
    fun get_withValidExitInfo_shouldNotReturnNull() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(mockExitInfo.processName).thenReturn(Application.getProcessName())
        whenever(activityManager.getHistoricalProcessExitReasons(anyOrNull(), any(), any())).thenReturn(listOf(mockExitInfo))

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.Valid).isTrue()
    }

    @Ignore("TODO(FranAguilera): BIT-5484. This works on gradle with Roboelectric. Fix on bazel")
    @Test
    fun get_withEmptyExitReason_shouldReturnEmpty() {
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(emptyList())

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.Empty).isTrue()
    }

    @Ignore("TODO(FranAguilera): BIT-5484. This works on gradle with Roboelectric. Fix on bazel")
    @Test
    fun get_withUnmatchedProcessName_shouldReturnProcessNameNotFound() {
        val mockExitInfo: ApplicationExitInfo = mock()
        whenever(
            activityManager
                .getHistoricalProcessExitReasons(anyOrNull(), any(), any()),
        ).thenReturn(listOf(mockExitInfo))

        val exitReason = latestAppExitInfoProvider.get(activityManager)

        assertThat(exitReason is LatestAppExitReasonResult.ProcessNameNotFound).isTrue()
    }
}
