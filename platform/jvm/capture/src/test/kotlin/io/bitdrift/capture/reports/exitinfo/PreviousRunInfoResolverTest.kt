// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PreviousRunInfoResolverTest {
    private val activityManager: ActivityManager = mock()
    private lateinit var resolver: PreviousRunInfoResolver

    @Before
    fun setUp() {
        LatestAppExitInfoProvider.clearCache()
    }

    @Test
    fun get_returnsFatalForCrashReason() {
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_CRASH)
        resolver =
            PreviousRunInfoResolver(
                activityManager = activityManager,
                latestAppExitInfoProvider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) },
            )

        val result = resolver.get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    fun get_returnsFatalForNativeCrash() {
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_CRASH_NATIVE)
        resolver =
            PreviousRunInfoResolver(
                activityManager = activityManager,
                latestAppExitInfoProvider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) },
            )

        val result = resolver.get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.NativeCrash),
        )
    }

    @Test
    fun get_returnsFatalForAnr() {
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_ANR)
        resolver =
            PreviousRunInfoResolver(
                activityManager = activityManager,
                latestAppExitInfoProvider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) },
            )

        val result = resolver.get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.AppNotResponding),
        )
    }

    @Test
    fun get_returnsNonFatalForUserRequested() {
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_USER_REQUESTED)
        resolver =
            PreviousRunInfoResolver(
                activityManager = activityManager,
                latestAppExitInfoProvider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) },
            )

        val result = resolver.get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false, terminationReason = ExitReason.UserRequested),
        )
    }

    @Test
    fun get_returnsNonFatalWhenNoExitInfo() {
        resolver =
            PreviousRunInfoResolver(
                activityManager = activityManager,
                latestAppExitInfoProvider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.None },
            )

        val result = resolver.get()

        assertThat(result).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false))
    }

    @Test
    fun get_returnsNullOnError() {
        resolver =
            PreviousRunInfoResolver(
                activityManager = activityManager,
                latestAppExitInfoProvider =
                    ILatestAppExitInfoProvider {
                        LatestAppExitReasonResult.Error("test error")
                    },
            )

        val result = resolver.get()

        assertThat(result).isNull()
    }

    @Test
    @Config(sdk = [24])
    fun get_belowApi30_returnsNull() {
        resolver = PreviousRunInfoResolver(activityManager = activityManager)

        val result = resolver.get()

        assertThat(result).isNull()
    }
}
