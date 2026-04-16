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
import io.bitdrift.capture.MockPreferences
import io.bitdrift.capture.fakes.FakeLatestAppExitInfoProvider
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PreviousRunInfoResolverTest {
    private val latestAppExitInfoProvider = FakeLatestAppExitInfoProvider()
    private val preferences = MockPreferences()
    private val previousRunInfoStateStore = LegacyPreviousRunStateStore(preferences)
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = mock()

    @Before
    fun tearDown() {
        latestAppExitInfoProvider.reset()
    }

    @Test
    fun get_returnsFatalForCrashReason() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    fun get_returnsFatalForNativeCrash() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_CRASH_NATIVE)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.NativeCrash),
        )
    }

    @Test
    fun get_returnsFatalForAnr() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_ANR)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.AppNotResponding),
        )
    }

    @Test
    fun get_returnsNonFatalForUserRequested() {
        latestAppExitInfoProvider.setAsValidReason(exitReasonType = ApplicationExitInfo.REASON_USER_REQUESTED)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false, terminationReason = ExitReason.UserRequested),
        )
    }

    @Test
    fun get_returnsNonFatalWhenNoExitInfo() {
        latestAppExitInfoProvider.setAsEmptyReason()

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false))
    }

    @Test
    fun get_returnsNullOnError() {
        latestAppExitInfoProvider.setAsErrorResult()

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isNull()
    }

    @Test
    @Config(sdk = [24])
    fun get_belowApi30_returnsNullWhenNoPreviousStateWasPersisted() {
        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isNull()
    }

    @Test
    @Config(sdk = [24])
    fun get_belowApi30_returnsNonFatalWhenStartedStateWasPersisted() {
        previousRunInfoStateStore.writeState(LegacyPreviousRunState.Started)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false))
    }

    @Test
    @Config(sdk = [24])
    fun get_belowApi30_returnsJvmCrashWhenCrashMarkerWasPersisted() {
        previousRunInfoStateStore.writeState(LegacyPreviousRunState.Started)
        previousRunInfoStateStore.writeState(LegacyPreviousRunState.JvmCrash)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    @Config(sdk = [24])
    fun get_belowApi30_ignoresUnknownPersistedState() {
        preferences.setString("io.bitdrift.capture.previous_run_info.state", "unknown", blocking = true)

        val result = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler).get()

        assertThat(result).isNull()
    }

    @Test
    @Config(sdk = [24])
    fun init_belowApi30_shouldInstallCrashHandler() {
        val resolver = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler)

        verify(captureUncaughtExceptionHandler).install(resolver)
    }

    @Test
    @Config(sdk = [30])
    fun init_Api30_shouldNotInstallCrashHandler() {
        val resolver = PreviousRunInfoResolver(latestAppExitInfoProvider, preferences, captureUncaughtExceptionHandler)

        verify(captureUncaughtExceptionHandler, never()).install(resolver)
    }
}
