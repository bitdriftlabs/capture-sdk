// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.MockPreferences
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.utils.BuildVersionChecker
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PreviousRunInfoResolverTest {
    private val latestAppExitInfoProvider = FakeLatestAppExitInfoProvider()
    private val preferences = MockPreferences()
    private val previousRunInfoBelowApi30Store = PreviousRunInfoBelowApi30Store(preferences)
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()
    private val buildVersionChecker: BuildVersionChecker = mock()

    @Before
    fun tearDown() {
        latestAppExitInfoProvider.reset()
        whenever(buildVersionChecker.isAtLeast(anyInt())).thenReturn(true)
    }

    @Test
    fun get_returnsFatalForCrashReason() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    fun get_returnsFatalForNativeCrash() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.NativeCrash),
        )
    }

    @Test
    fun get_returnsFatalForAnr() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_ANR)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.AppNotResponding),
        )
    }

    @Test
    fun get_returnsNonFatalForUserRequested() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_USER_REQUESTED)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false, terminationReason = ExitReason.UserRequested),
        )
    }

    @Test
    fun get_returnsNonFatalWhenNoExitInfo() {
        latestAppExitInfoProvider.setAsEmptyReason()

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false))
    }

    @Test
    fun get_returnsNullOnError() {
        latestAppExitInfoProvider.setAsErrorResult()

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isNull()
    }

    @Test
    fun get_belowApi30_returnsNullWhenNoPreviousStateWasPersisted() {
        whenever(buildVersionChecker.isAtLeast(android.os.Build.VERSION_CODES.R)).thenReturn(false)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isNull()
    }

    @Test
    fun get_belowApi30_returnsNonFatalWhenStartedStateWasPersisted() {
        whenever(buildVersionChecker.isAtLeast(android.os.Build.VERSION_CODES.R)).thenReturn(false)
        previousRunInfoBelowApi30Store.writeState(PreviousRunInfoBelowApi30State.Started)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false))
    }

    @Test
    fun get_belowApi30_returnsJvmCrashWhenCrashMarkerWasPersisted() {
        whenever(buildVersionChecker.isAtLeast(android.os.Build.VERSION_CODES.R)).thenReturn(false)
        previousRunInfoBelowApi30Store.writeState(PreviousRunInfoBelowApi30State.Started)
        previousRunInfoBelowApi30Store.writeState(PreviousRunInfoBelowApi30State.JvmCrash)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    fun get_belowApi30_ignoresUnknownPersistedState() {
        whenever(buildVersionChecker.isAtLeast(android.os.Build.VERSION_CODES.R)).thenReturn(false)
        preferences.setString("io.bitdrift.capture.previous_run_info.state", "unknown", blocking = true)

        val result =
            PreviousRunInfoResolver(
                latestAppExitInfoProvider,
                preferences,
                captureUncaughtExceptionHandler,
                buildVersionChecker,
            ).get()

        assertThat(result).isNull()
    }

    @Test
    fun init_belowApi30_shouldInstallCrashHandler() {
        whenever(buildVersionChecker.isAtLeast(android.os.Build.VERSION_CODES.R)).thenReturn(false)

        val resolver = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler, buildVersionChecker)

        verify(captureUncaughtExceptionHandler).install(resolver)
    }

    @Test
    fun init_Api30_shouldNotInstallCrashHandler() {
        val resolver = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler, buildVersionChecker)

        verify(captureUncaughtExceptionHandler, never()).install(resolver)
    }
}
