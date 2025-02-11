// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.providers.session.SessionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ConfigurationTest {
    private val captureStartListener: ICaptureStartListener = mock()

    @Test
    fun configurationFailure() {
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
            ),
        ).thenReturn(-1L)

        // We start without configured logger.
        assertThat(Capture.logger()).isNull()

        startLoggerWithDefault(bridge)

        // The configuration failed so the logger is still `null`.
        assertThat(Capture.logger()).isNull()

        // We confirm that we actually tried to configure the logger.
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
        )

        val sdkNotStartedErrorCaptor = argumentCaptor<SdkNotStartedError>()
        verify(captureStartListener).onStartFailure(sdkNotStartedErrorCaptor.capture())
        verify(captureStartListener, never()).onStartSuccess()
        assertThat(sdkNotStartedErrorCaptor.lastValue.message)
            .isEqualTo("Failed to start Capture. initialization of the rust logger failed")

        // We perform another attempt to configure the logger to verify that
        // consecutive configure calls are no-ops.
        startLoggerWithDefault(bridge)

        assertThat(Capture.logger()).isNull()

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
        )
        verifyNoMoreInteractions(captureStartListener)
    }

    @After
    fun tearDown() {
        Capture.Logger.resetShared()
    }

    private fun startLoggerWithDefault(bridge: IBridge) {
        Capture.Logger.start(
            apiKey = "test1",
            sessionStrategy = SessionStrategy.Fixed(),
            dateProvider = null,
            bridge = bridge,
            captureStartListener = captureStartListener,
        )
    }
}
