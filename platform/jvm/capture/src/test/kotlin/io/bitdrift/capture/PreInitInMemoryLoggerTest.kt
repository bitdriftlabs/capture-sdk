// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.fieldsOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PreInitInMemoryLoggerTest {
    private val logger: IInternalLogger = mock()
    private val preInitLogger = PreInitInMemoryLogger()

    @Test
    fun `identity properties expose placeholders`() {
        assertThat(preInitLogger.sessionId).isEqualTo("unknown")
        assertThat(preInitLogger.sessionUrl).isEqualTo("unknown")
        assertThat(preInitLogger.deviceId).isEqualTo("unknown")
        assertThat(preInitLogger.isTracingActive).isFalse()
    }

    @Test
    fun `buffered calls are replayed`() {
        preInitLogger.startNewSession("session-id")
        preInitLogger.addField("key", "value")
        preInitLogger.setFeatureFlagExposure("flag", "variant")
        preInitLogger.setEntityId("entity")
        preInitLogger.clearEntityId()
        preInitLogger.setSleepMode(SleepMode.ENABLED)

        flush()

        verify(logger).startNewSession("session-id")
        verify(logger).addField("key", "value")
        verify(logger).setFeatureFlagExposure("flag", "variant")
        verify(logger).setEntityId("entity")
        verify(logger).clearEntityId()
        verify(logger).setSleepMode(SleepMode.ENABLED)
    }

    @Test
    fun `representative SDK calls replay with their original arguments`() {
        val completion: (CaptureResult<String>) -> Unit = {}
        val parentSpanId = UUID.randomUUID()
        val request: HttpRequestInfo = mock()
        val response: HttpResponseInfo = mock()

        preInitLogger.createTemporaryDeviceCode(completion)
        preInitLogger.setFeatureFlagExposure("boolean-flag", true)
        preInitLogger.logAppLaunchTTI(123.milliseconds)
        preInitLogger.logScreenView("home")
        val span = preInitLogger.startSpan("operation", LogLevel.DEBUG, mapOf("key" to "value"), 456L, parentSpanId)
        preInitLogger.log(request)
        preInitLogger.log(response)

        flush()

        verify(logger).createTemporaryDeviceCode(completion)
        verify(logger).setFeatureFlagExposure("boolean-flag", true)
        verify(logger).logAppLaunchTTI(123.milliseconds)
        verify(logger).logScreenView("home")
        verify(logger).startSpan("operation", LogLevel.DEBUG, mapOf("key" to "value"), 456L, parentSpanId)
        verify(logger).log(request)
        verify(logger).log(response)
        span.end(SpanResult.SUCCESS)
        verify(logger, never()).logInternal(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun `log preserves call time message fields throwable and timestamp`() {
        var message = "before"

        assertBufferedLog(
            expectedFields =
                fieldsOf(
                    "key" to "value",
                    "_error" to IllegalStateException::class.java.name,
                    "_error_details" to "failure",
                ),
        ) {
            preInitLogger.log(
                level = LogLevel.INFO,
                fields = mapOf("key" to "value"),
                throwable = IllegalStateException("failure"),
            ) { message }
            message = "after"
        }
    }

    @Test
    fun `optimized log overload preserves fields and throwable`() {
        assertBufferedLog(
            expectedFields =
                fieldsOf(
                    "key" to "value",
                    "_error" to IllegalArgumentException::class.java.name,
                    "_error_details" to "invalid",
                ),
        ) {
            preInitLogger.log(
                level = LogLevel.INFO,
                arrayFields = fieldsOf("key" to "value"),
                throwable = IllegalArgumentException("invalid"),
            ) { "before" }
        }
    }

    @Test
    fun `clear discards buffered calls`() {
        preInitLogger.addField("key", "value")

        preInitLogger.clear()
        flush()

        verify(logger, never()).addField(any(), any())
    }

    @Test
    fun `calls made after flush replay immediately and only once`() {
        flush()

        preInitLogger.addField("key", "value")
        flush()

        verify(logger, times(1)).addField("key", "value")
    }

    @Test
    fun `oversized call is dropped and reported`() {
        preInitLogger.log(LogLevel.INFO) { "x".repeat(MAX_BUFFER_BYTES) }

        flush()

        verifyNormalLogNotWritten()
        val warning = captureLog(LogType.INTERNALSDK)
        assertThat(warning.level).isEqualTo(LogLevel.WARNING)
        assertThat(warning.fields).isEqualTo(fieldsOf("dropped_call_count" to "1"))
        assertThat(warning.message).contains("new buffered calls were dropped")
    }

    private fun flush() {
        preInitLogger.flushToNative(logger)
    }

    private fun assertBufferedLog(
        expectedFields: ArrayFields,
        enqueue: () -> Unit,
    ) {
        val beforeLogMs = System.currentTimeMillis()
        enqueue()
        val afterLogMs = System.currentTimeMillis()
        flush()

        val replayedLog = captureLog(LogType.NORMAL)
        assertThat(replayedLog.level).isEqualTo(LogLevel.INFO)
        assertThat(replayedLog.fields).isEqualTo(expectedFields)
        assertThat(replayedLog.message).isEqualTo("before")
        assertThat(replayedLog.occurredAtMs).isBetween(beforeLogMs, afterLogMs)
    }

    private fun captureLog(type: LogType): ReplayedLog {
        val level = argumentCaptor<LogLevel>()
        val fields = argumentCaptor<ArrayFields>()
        val attributes = argumentCaptor<LogAttributesOverrides>()
        val message = argumentCaptor<() -> String>()

        verify(logger).logInternal(
            eq(type),
            level.capture(),
            fields.capture(),
            eq(ArrayFields.EMPTY),
            attributes.capture(),
            eq(false),
            message.capture(),
        )

        return ReplayedLog(
            level = level.firstValue,
            fields = fields.firstValue,
            occurredAtMs = (attributes.firstValue as? LogAttributesOverrides.OccurredAt)?.occurredAtTimestampMs,
            message = message.firstValue(),
        )
    }

    private fun verifyNormalLogNotWritten() {
        verify(logger, never()).logInternal(
            eq(LogType.NORMAL),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    private data class ReplayedLog(
        val level: LogLevel,
        val fields: ArrayFields,
        val occurredAtMs: Long?,
        val message: String,
    )

    private companion object {
        private const val MAX_BUFFER_BYTES = 512 * 1024
    }
}
