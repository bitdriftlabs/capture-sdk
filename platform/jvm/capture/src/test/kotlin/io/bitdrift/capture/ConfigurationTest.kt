// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.providers.session.SessionConfiguration
import io.bitdrift.capture.providers.session.SessionStrategy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ConfigurationTest {
    @Test
    fun configurationFailure() {
        val targetDomain = "http://capture.example:8080"
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        val bridge: IBridge = mock {}
        whenever(
            bridge.createLogger(
                anyOrNull(),
                anyOrNull(),
                eq(targetDomain),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(-1L)

        // We start without configured logger.
        Assertions.assertThat(Capture.logger()).isNull()

        Capture.Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
            apiUrl = targetDomain.toHttpUrl(),
            bridge = bridge,
        )

        // The configuration failed so the logger is still `null`.
        Assertions.assertThat(Capture.logger()).isNull()

        // We confirm that we actually tried to configure the logger.
        verify(bridge, times(1)).createLogger(
            anyOrNull(),
            anyOrNull(),
            eq(targetDomain),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )

        // We perform another attempt to configure the logger to verify that
        // consecutive configure calls are no-ops.
        Capture.Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
            bridge = bridge,
        )

        Assertions.assertThat(Capture.logger()).isNull()

        // We verify that the second configure call was a no-op.
        verify(bridge, times(1)).createLogger(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun startResult_emitsFailure_whenBridgeReturnsInvalidLogger() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())

        val bridge: IBridge = mock {}
        whenever(
            bridge.createLogger(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(-1L)

        var capturedResult: CaptureResult<ILogger>? = null

        Capture.Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
            dateProvider = null,
            bridge = bridge,
        ) { result ->
            capturedResult = result
        }

        Assertions.assertThat(capturedResult).isInstanceOf(CaptureResult.Failure::class.java)
        val failure = capturedResult as CaptureResult.Failure
        val error = failure.error as SdkStartFailure
        Assertions.assertThat(error.reason).startsWith("Failed to start Capture:")
        Assertions.assertThat(error.cause).isNotNull()
    }

    @After
    fun tearDown() {
        Capture.Logger.resetShared()
    }
}
