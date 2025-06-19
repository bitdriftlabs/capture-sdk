// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration

class PreInitInMemoryLoggerTest {
    private val preInitInMemoryLogger = PreInitInMemoryLogger()
    private val testLogger = TestLogger()

    @Before
    fun setup() {
        preInitInMemoryLogger.clear()
        testLogger.clear()
    }

    @Test
    fun log_withoutOverwriteOldest_shouldMatchExpectedCalls() {
        val totalCalls = 100

        triggerScreenViewCalls(totalCalls = totalCalls)

        assertThat(testLogger.screenNameViewed.size).isEqualTo(totalCalls)
        assertThat(testLogger.screenNameViewed).last().isEqualTo("Screen Viewed 100")
    }

    @Test
    fun log_withMaxSizeReached_shouldRemoveFirstEntryAndKeepLast() {
        val totalCalls = 2048

        triggerScreenViewCalls(totalCalls = totalCalls)

        assertThat(testLogger.screenNameViewed.size).isEqualTo(512)
        assertThat(testLogger.screenNameViewed).first().isEqualTo("Screen Viewed 1537")
        assertThat(testLogger.screenNameViewed).last().isEqualTo("Screen Viewed 2048")
    }

    private fun triggerScreenViewCalls(totalCalls: Int) {
        for (i in 1..totalCalls) {
            preInitInMemoryLogger.logScreenView("Screen Viewed $i")
        }

        preInitInMemoryLogger.bufferedLoggerCalls.forEach { it(testLogger) }
    }

    @Suppress("EmptyFunctionBlock")
    private class TestLogger : ILogger {
        val screenNameViewed = mutableListOf<String>()

        override val sessionId: String = "test-session"

        override val sessionUrl: String = "test-url"

        override val deviceId: String = "test-device"

        override fun startNewSession() {}

        override fun createTemporaryDeviceCode(completion: (CaptureResult<String>) -> Unit) {}

        override fun addField(
            key: String,
            value: String,
        ) {}

        override fun removeField(key: String) {}

        override fun logScreenView(screenName: String) {
            screenNameViewed.add(screenName)
        }

        override fun log(
            level: LogLevel,
            fields: Map<String, String>?,
            throwable: Throwable?,
            message: () -> String,
        ) {}

        override fun logAppLaunchTTI(duration: Duration) {}

        override fun startSpan(
            name: String,
            level: LogLevel,
            fields: Map<String, String>?,
            startTimeMs: Long?,
            parentSpanId: UUID?,
        ): Span = Span(null, name, level, fields, startTimeMs, parentSpanId)

        override fun log(httpRequestInfo: HttpRequestInfo) {}

        override fun log(httpResponseInfo: HttpResponseInfo) {}

        fun clear() {
            screenNameViewed.clear()
        }
    }
}
