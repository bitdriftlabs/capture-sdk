// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ApplicationExitInfo
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PreviousRunInfoResolverTest {
    private val latestAppExitInfoProvider = FakeLatestAppExitInfoProvider()

    @Before
    fun tearDown() {
        latestAppExitInfoProvider.reset()
    }

    @Test
    fun get_returnsFatalForCrashReason() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    fun get_returnsFatalForNativeCrash() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.NativeCrash),
        )
    }

    @Test
    fun get_returnsFatalForAnr() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_ANR)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.AppNotResponding),
        )
    }

    @Test
    fun get_returnsNonFatalForUserRequested() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_USER_REQUESTED)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false, terminationReason = ExitReason.UserRequested),
        )
    }

    @Test
    fun get_returnsNonFatalWhenNoExitInfo() {
        latestAppExitInfoProvider.setAsEmptyReason()

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false))
    }

    @Test
    fun get_returnsNullOnError() {
        latestAppExitInfoProvider.setAsErrorResult()

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isNull()
    }

    @Test
    @Config(sdk = [24])
    fun get_belowApi30_returnsNull() {
        val result = PreviousRunInfoResolver(latestAppExitInfoProvider).get()

        assertThat(result).isNull()
    }
}
