// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.session.SessionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CaptureTest {
    private val loggerStateListener: LoggerStateListener = mock()
    private val loggerStateCaptor = argumentCaptor<LoggerState>()

    // This Test needs to run first since the following tests need to initialize
    // the ContextHolder before they can run.
    @Test
    fun aConfigureSkipsLoggerCreationWhenContextNotInitialized() {
        assertThat(Capture.logger()).isNull()

        startLogger(apiKey = "test1")

        assertThat(Capture.logger()).isNull()
        verify(loggerStateListener, never()).onLoggerStateUpdate(any())
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
    fun cIdempotentConfigure() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        assertThat(Capture.logger()).isNull()

        startLogger(apiKey = "test1")

        val logger = Capture.logger()
        assertThat(logger).isNotNull()
        assertThat(Logger.deviceId).isNotNull()
        verifyLoggerStateSuccessListener()

        startLogger(apiKey = "test2")
        verifyLoggerStateSuccessListener()

        // Calling reconfigure a second time does not change the static logger.
        assertThat(logger).isEqualTo(Capture.logger())
    }

    @Test
    fun loggerStart_withEmptyApiKey_shouldEmitApiKeyErrorFailure() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        startLogger(apiKey = "")

        assertThat(Capture.logger()).isNull()
        verifyApiKeyError()
    }

    private fun verifyApiKeyError() {
        verify(loggerStateListener, times(1)).onLoggerStateUpdate(loggerStateCaptor.capture())
        val latestEmission = loggerStateCaptor.lastValue
        assertThat(latestEmission is LoggerState.StartFailure).isTrue()
        val apiKeyErrorMessage =
            when (latestEmission) {
                is LoggerState.StartFailure -> latestEmission.throwable.message
                else -> ""
            }
        assertThat(apiKeyErrorMessage).isEqualTo("API key is empty")
    }

    private fun verifyLoggerStateSuccessListener() {
        verify(loggerStateListener, times(2))
            .onLoggerStateUpdate(loggerStateCaptor.capture())
        assertThat(loggerStateCaptor.firstValue is LoggerState.Starting).isTrue()
        assertThat(loggerStateCaptor.secondValue is LoggerState.Started).isTrue()
    }

    private fun startLogger(apiKey: String) {
        Logger.start(
            apiKey = apiKey,
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
            loggerStateListener = loggerStateListener,
        )
    }
}
