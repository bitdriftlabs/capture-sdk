// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.reports.exitinfo.ExitReason
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.exitinfo.PreviousRunInfo
import io.bitdrift.capture.reports.exitinfo.PreviousRunInfoResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CaptureTest {
    // This Test needs to run first since the following tests need to initialize
    // the ContextHolder before they can run.
    @Test
    fun aConfigureSkipsLoggerCreationWhenContextNotInitialized() {
        assertThat(Capture.logger()).isNull()

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        )

        assertThat(Capture.logger()).isNull()
    }

    // Accessing fields prior to the configuration of the logger may lead to crash since it can
    // potentially call into a native method that's used to sanitize passed url path.
    @Test
    fun bDoesNotAccessFieldsIfLoggerNotConfigured() {
        assertThat(Capture.logger()).isNull()

        val requestInfo = HttpRequestInfo("GET", path = HttpUrlPath("/foo/12345"))
        Logger.log(requestInfo)

        val responseInfo =
            HttpResponseInfo(
                requestInfo,
                response =
                    HttpResponse(
                        result = HttpResponse.HttpResult.SUCCESS,
                        path = HttpUrlPath("/foo_path/12345"),
                    ),
                durationMs = 60L,
            )
        Logger.log(responseInfo)

        assertThat(Capture.logger()).isNull()
    }

    @Test
    @OptIn(ExperimentalBitdriftApi::class)
    fun bGetPreviousRunInfoNoopWhenLoggerNotConfigured() {
        assertThat(Capture.logger()).isNull()
        assertThat(Logger.getPreviousRunInfo()).isNull()
    }

    @Test
    fun cIdempotentConfigure() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        assertThat(Capture.logger()).isNull()

        Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        )

        val logger = Capture.logger()
        assertThat(logger).isNotNull()
        assertThat(Logger.deviceId).isNotNull()

        Logger.start(
            apiKey = "test2",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        )

        // Calling reconfigure a second time does not change the static logger.
        assertThat(logger).isEqualTo(Capture.logger())
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsCrashForCrashReasons() {
        val activityManager: ActivityManager = mock()
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_CRASH)
        val provider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) }

        val previousRunInfo = PreviousRunInfoResolver(mock(), provider).get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = true, terminationReason = ExitReason.JvmCrash),
        )
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsNoCrashForNonCrashReason() {
        val activityManager: ActivityManager = mock()
        val appExitInfo: ApplicationExitInfo = mock()
        whenever(appExitInfo.reason).thenReturn(ApplicationExitInfo.REASON_EXIT_SELF)
        val provider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.Valid(appExitInfo) }

        val previousRunInfo = PreviousRunInfoResolver(mock(), provider).get(activityManager)

        assertThat(previousRunInfo).isEqualTo(
            PreviousRunInfo(hasFatallyTerminated = false, terminationReason = ExitReason.ExitSelf),
        )
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsNoCrashWhenNoExitInfo() {
        val activityManager: ActivityManager = mock()
        val provider = ILatestAppExitInfoProvider { LatestAppExitReasonResult.None }

        val previousRunInfo = PreviousRunInfoResolver(mock(), provider).get(activityManager)

        assertThat(previousRunInfo).isEqualTo(PreviousRunInfo(hasFatallyTerminated = false, terminationReason = null))
    }

    @Test
    @Config(sdk = [30])
    fun getPreviousRunInfo_returnsNullWhenProviderFails() {
        val activityManager: ActivityManager = mock()
        val provider =
            ILatestAppExitInfoProvider {
                LatestAppExitReasonResult.Error(
                    "Failed to read app exit info",
                    RuntimeException("boom"),
                )
            }

        val previousRunInfo = PreviousRunInfoResolver(mock(), provider).get(activityManager)

        assertThat(previousRunInfo).isNull()
    }
}
