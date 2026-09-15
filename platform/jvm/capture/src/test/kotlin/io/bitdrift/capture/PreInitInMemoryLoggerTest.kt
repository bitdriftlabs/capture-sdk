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
import io.bitdrift.capture.events.span.SpanField
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.DateProvider
import io.bitdrift.capture.providers.fieldsOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PreInitInMemoryLoggerTest {
    private val logger: IInternalLogger = mock()
    private val preInitInMemoryLogger = PreInitInMemoryLogger()

    @Test
    fun `identity properties expose placeholders`() {
        assertThat(preInitInMemoryLogger.sessionId).isEqualTo("unknown")
        assertThat(preInitInMemoryLogger.sessionUrl).isEqualTo("unknown")
        assertThat(preInitInMemoryLogger.deviceId).isEqualTo("unknown")
        assertThat(preInitInMemoryLogger.isTracingActive).isFalse()
    }

    @Test
    fun `buffered calls are replayed`() {
        preInitInMemoryLogger.startNewSession("session-id")
        preInitInMemoryLogger.addField("key", "value")
        preInitInMemoryLogger.setFeatureFlagExposure("flag", "variant")
        preInitInMemoryLogger.setEntityId("entity")
        preInitInMemoryLogger.clearEntityId()
        preInitInMemoryLogger.setSleepMode(SleepMode.ENABLED)

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
        preInitInMemoryLogger.setFeatureFlagExposure("boolean-flag", true)
        preInitInMemoryLogger.logAppLaunchTTI(123.milliseconds)
        preInitInMemoryLogger.logScreenView("home")

        flush()

        verify(logger).setFeatureFlagExposure("boolean-flag", true)
        verify(logger).logAppLaunchTTI(123.milliseconds)
        verify(logger).logScreenView("home")
    }

    @Test
    fun `http request and response replay with their fields and call time`() {
        val request = HttpRequestInfo(method = "POST", host = "example.com")
        val response =
            HttpResponseInfo(
                request = request,
                response = HttpResponse(result = HttpResponse.HttpResult.SUCCESS, statusCode = 200),
                durationMs = 42L,
            )

        val beforeMs = System.currentTimeMillis()
        preInitInMemoryLogger.log(request)
        preInitInMemoryLogger.log(response)
        val afterMs = System.currentTimeMillis()

        flush()

        val fields = argumentCaptor<ArrayFields>()
        val attributes = argumentCaptor<LogAttributesOverrides>()
        verify(logger, times(2)).logInternal(
            eq(LogType.SPAN),
            eq(LogLevel.DEBUG),
            fields.capture(),
            any(),
            attributes.capture(),
            eq(false),
            any(),
        )

        assertThat(fields.firstValue[SpanField.Key.TYPE]).isEqualTo("start")
        assertThat(fields.secondValue[SpanField.Key.TYPE]).isEqualTo("end")
        attributes.allValues.forEach {
            val occurredAtMs = (it as LogAttributesOverrides.OccurredAt).occurredAtTimestampMs
            assertThat(occurredAtMs).isBetween(beforeMs, afterMs)
        }
    }

    @Test
    fun `createTemporaryDeviceCode fails immediately instead of being buffered`() {
        // Covers the fallback for a direct call; Capture.Logger.createTemporaryDeviceCode
        // already skips this instance while starting.
        val results = mutableListOf<CaptureResult<String>>()

        preInitInMemoryLogger.createTemporaryDeviceCode { results.add(it) }

        assertThat(results).containsExactly(CaptureResult.Failure(SdkNotStartedError))
        flush()
        verify(logger, never()).createTemporaryDeviceCode(any())
    }

    @Test
    fun `span started during pre-init emits both start and end logs once flushed`() {
        val parentSpanId = UUID.randomUUID()

        val span = preInitInMemoryLogger.startSpan("operation", LogLevel.DEBUG, mapOf("key" to "value"), 456L, parentSpanId)

        flush()

        span.end(SpanResult.SUCCESS)

        val types = argumentCaptor<ArrayFields>()
        val attributes = argumentCaptor<LogAttributesOverrides>()
        verify(logger, times(2)).logInternal(
            eq(LogType.SPAN),
            eq(LogLevel.DEBUG),
            types.capture(),
            eq(ArrayFields.EMPTY),
            attributes.capture(),
            eq(false),
            any(),
        )
        assertThat(types.firstValue[SpanField.Key.DURATION]).isNull()
        assertThat(types.secondValue[SpanField.Key.DURATION]).isNotNull()
    }

    @Test
    fun `span ended before the SDK finishes starting still emits both logs once flushed`() {
        // The Span always holds this buffer as its logger, so end() called before the SDK
        // finishes starting buffers the end log the same way the start log is buffered,
        // instead of being dropped.
        val span = preInitInMemoryLogger.startSpan("operation", LogLevel.DEBUG, null, null, null)

        span.end(SpanResult.SUCCESS)
        flush()

        val attributes = argumentCaptor<LogAttributesOverrides>()
        verify(logger, times(2)).logInternal(
            eq(LogType.SPAN),
            any(),
            any(),
            eq(ArrayFields.EMPTY),
            attributes.capture(),
            eq(false),
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
            preInitInMemoryLogger.log(
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
            preInitInMemoryLogger.log(
                level = LogLevel.INFO,
                arrayFields = fieldsOf("key" to "value"),
                throwable = IllegalArgumentException("invalid"),
            ) { "before" }
        }
    }

    @Test
    fun `cleanUp discards buffered calls`() {
        preInitInMemoryLogger.addField("key", "value")

        preInitInMemoryLogger.cleanUp()
        flush()

        verify(logger, never()).addField(any(), any())
    }

    @Test
    fun `calls made after flush replay immediately and only once`() {
        flush()

        preInitInMemoryLogger.addField("key", "value")
        flush()

        verify(logger, times(1)).addField("key", "value")
    }

    @Test
    fun `log honors a supplied DateProvider instead of the system clock`() {
        val fakeNow = Date(123_456_789L)
        val loggerWithFakeClock = PreInitInMemoryLogger(dateProvider = DateProvider { fakeNow })

        loggerWithFakeClock.log(LogLevel.INFO) { "message" }
        loggerWithFakeClock.flushToNative(logger)

        val occurredAtMs = argumentCaptor<LogAttributesOverrides>()
        verify(logger).logInternal(
            eq(LogType.NORMAL),
            eq(LogLevel.INFO),
            any(),
            eq(ArrayFields.EMPTY),
            occurredAtMs.capture(),
            eq(false),
            any(),
        )
        assertThat((occurredAtMs.firstValue as LogAttributesOverrides.OccurredAt).occurredAtTimestampMs).isEqualTo(123_456_789L)
    }

    @Test
    fun `oversized call is dropped and reported`() {
        preInitInMemoryLogger.log(LogLevel.INFO) { "x".repeat(MAX_BUFFER_BYTES) }

        flush()

        verifyNormalLogNotWritten()
        val warning = captureLog(LogType.INTERNALSDK)
        assertThat(warning.level).isEqualTo(LogLevel.WARNING)
        assertThat(warning.fields).isEqualTo(fieldsOf("dropped_call_count" to "1"))
        assertThat(warning.message).contains("new buffered calls were dropped")
    }

    private fun flush() {
        preInitInMemoryLogger.flushToNative(logger)
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
