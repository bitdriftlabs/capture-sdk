// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.exitinfo

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.IInternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PreviousRunInfoResolverTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val internalLogger: IInternalLogger = mock()
    private val activityManager: ActivityManager = mock()
    private lateinit var previousRunInfoResolver: PreviousRunInfoResolver

    @Before
    fun setUp() {
        previousRunInfoResolver = PreviousRunInfoResolver(internalLogger)
    }

    @Test
    fun initLegacyWatcher_whenNoStateFile_shouldReturnsNoCrash() {
        previousRunInfoResolver.initLegacyWatcher(tempFolder.root.absolutePath)

        val previousRunInfo = previousRunInfoResolver.get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false),
        )
    }

    @Test
    fun get_afterPersistJvmCrashState_returnsCrashOnNextInit() {
        val sdkDir = tempFolder.root.absolutePath

        previousRunInfoResolver.initLegacyWatcher(sdkDir)
        previousRunInfoResolver.persistJvmCrashState()

        val nextRunResolver = PreviousRunInfoResolver(internalLogger)
        nextRunResolver.initLegacyWatcher(sdkDir)
        val previousRunInfo = nextRunResolver.get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, reason = ExitReason.JvmCrash),
        )
    }

    @Test
    fun initLegacyWatcher_resetsStateForCurrentRun() {
        val sdkDir = tempFolder.root.absolutePath

        previousRunInfoResolver.initLegacyWatcher(sdkDir)
        previousRunInfoResolver.persistJvmCrashState()

        val secondResolver = PreviousRunInfoResolver(internalLogger)
        secondResolver.initLegacyWatcher(sdkDir)

        val thirdResolver = PreviousRunInfoResolver(internalLogger)
        thirdResolver.initLegacyWatcher(sdkDir)
        val previousRunInfo = thirdResolver.get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false),
        )
    }

    @Test
    fun get_beforeInitLegacyWatcher_returnsNull() {
        val previousRunInfo = previousRunInfoResolver.get(activityManager)

        assertThat(previousRunInfo).isNull()
    }

    @Test
    fun persistJvmCrashState_beforeInit_logsInternalError() {
        previousRunInfoResolver.persistJvmCrashState()

        verify(internalLogger).logInternalError(
            eq(null),
            eq(false),
            any(),
        )
    }

    @Test
    @Config(sdk = [30])
    fun initLegacyWatcher_whenApi30_shouldBeNoop() {
        val sdkDir = tempFolder.root.absolutePath
        previousRunInfoResolver.initLegacyWatcher(sdkDir)

        assertThat(tempFolder.root.resolve("reports/previous_run_info.state").exists()).isFalse()
    }

    @Test
    @Config(sdk = [30])
    fun persistJvmCrashState_whenApi30_shouldBeNoop() {
        val sdkDir = tempFolder.root.absolutePath
        previousRunInfoResolver.initLegacyWatcher(sdkDir)
        previousRunInfoResolver.persistJvmCrashState()

        assertThat(tempFolder.root.resolve("reports/previous_run_info.state").exists()).isFalse()
        verify(internalLogger, never()).logInternalError(any(), any(), any())
    }

    @Test
    @Config(sdk = [30])
    fun get_onApi30_returnsFatalForCrashReason() {
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_CRASH)
        val provider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) }
        val resolver = PreviousRunInfoResolver(internalLogger, provider)

        val previousRunInfo = resolver.get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, reason = ExitReason.JvmCrash),
        )
    }

    @Test
    @Config(sdk = [30])
    fun get_onApi30_returnsNonFatalForUserRequested() {
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_USER_REQUESTED)
        val provider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) }
        val resolver = PreviousRunInfoResolver(internalLogger, provider)

        val previousRunInfo = resolver.get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false, reason = ExitReason.UserRequested),
        )
    }
}
