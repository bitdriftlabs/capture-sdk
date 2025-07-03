// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.threading.CaptureDispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch

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
    fun startAsync_withMultipleCalls_shouldSetLoggerOnce() {
        CaptureDispatchers.setTestExecutorService(MoreExecutors.newDirectExecutorService())
        val latch = CountDownLatch(1)

        var completionResult: CaptureResult<ILogger>? = null
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
        // Expected null ILogger since there is no startAsync call yet
        assertThat(Capture.logger()).isNull()

        Logger.startAsync(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
        ) {
            completionResult = it
            latch.countDown()
        }
        latch.await()

        // After initial startAsync call, logger from completionResult
        // should match the one exposed directly from Capture
        assertThat(completionResult is CaptureResult.Success).isTrue()
        val initialLogger = Capture.logger()
        assertThat(initialLogger).isNotNull()
        assertThat(Logger.sessionId).isEqualTo(initialLogger?.sessionId)
        assertThat((completionResult as CaptureResult.Success<ILogger>).value).isEqualTo(initialLogger)

        Logger.startAsync(
            apiKey = "test2",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
            completionResult = {},
        )

        // Calling reconfigure a second time does not change the static logger.
        assertThat(initialLogger).isEqualTo(Capture.logger())
    }
}
