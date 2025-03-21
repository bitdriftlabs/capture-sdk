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
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.providers.toFieldValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SpanTest {
    private val logger: LoggerImpl = mock()
    private val clock: IClock = mock()

    @Test
    fun logs() {
        val span = Span(logger, "name", LogLevel.INFO, clock = clock)

        val fields = argumentCaptor<InternalFieldsMap>()
        span.end(SpanResult.SUCCESS)

        verify(logger, times(2)).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            fields.capture(),
            eq(null),
            eq(null),
            eq(false),
            any(),
        )

        // start
        assertThat(fields.firstValue).containsKey("_span_id")
        assertThat(fields.firstValue).containsEntry("_span_name", "name".toFieldValue())
        assertThat(fields.firstValue).containsEntry("_span_type", "start".toFieldValue())
        // end
        assertThat(fields.secondValue).containsKey("_span_id")
        assertThat(fields.secondValue).containsEntry("_span_name", "name".toFieldValue())
        assertThat(fields.secondValue).containsEntry("_span_type", "end".toFieldValue())
        assertThat(fields.secondValue).containsKey("_duration_ms")
        assertThat(fields.secondValue).containsEntry("_result", "success".toFieldValue())
    }

    @Test
    fun spansWithStartAndEnd() {
        val span = Span(logger, "name", LogLevel.INFO, clock = clock, customStartTimeMs = 1L)

        val fields = argumentCaptor<InternalFieldsMap>()
        span.end(SpanResult.SUCCESS, endTimeMs = 1000L)

        verify(logger, times(2)).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            fields.capture(),
            eq(null),
            eq(null),
            eq(false),
            any(),
        )

        // start
        assertThat(fields.firstValue).containsKey("_span_id")
        assertThat(fields.firstValue).containsEntry("_span_name", "name".toFieldValue())
        assertThat(fields.firstValue).containsEntry("_span_type", "start".toFieldValue())
        // end
        assertThat(fields.secondValue).containsKey("_span_id")
        assertThat(fields.secondValue).containsEntry("_duration_ms", "999".toFieldValue())
    }

    @Test
    fun spansWithStartAndNoEnd() {
        whenever(clock.elapsedRealtime()).thenReturn(0L)
        val spanWithNoEnd = Span(logger, "name", LogLevel.INFO, clock = clock, customStartTimeMs = 1L)
        val fieldsWithNoEnd = argumentCaptor<InternalFieldsMap>()
        whenever(clock.elapsedRealtime()).thenReturn(1337L)
        spanWithNoEnd.end(SpanResult.SUCCESS)

        verify(logger, times(2)).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            fieldsWithNoEnd.capture(),
            eq(null),
            eq(null),
            eq(false),
            any(),
        )

        assertThat(fieldsWithNoEnd.secondValue).containsKey("_span_id")
        assertThat(fieldsWithNoEnd.secondValue).containsEntry("_duration_ms", "1337".toFieldValue())
    }

    @Test
    fun spansWithNoStartAndEnd() {
        whenever(clock.elapsedRealtime()).thenReturn(1337L)
        val spanWithNoStart = Span(logger, "name", LogLevel.INFO, clock = clock)
        val fieldsWithNoStart = argumentCaptor<InternalFieldsMap>()
        whenever(clock.elapsedRealtime()).thenReturn(1338L)
        spanWithNoStart.end(SpanResult.SUCCESS, endTimeMs = 1337)

        verify(logger, times(2)).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            fieldsWithNoStart.capture(),
            eq(null),
            eq(null),
            eq(false),
            any(),
        )

        assertThat(fieldsWithNoStart.secondValue).containsKey("_span_id")
        assertThat(fieldsWithNoStart.secondValue).containsEntry("_duration_ms", "1".toFieldValue())
    }

    @Test
    fun spansNoEmit() {
        val span = Span(logger, "name", LogLevel.INFO, clock = clock, customStartTimeMs = 0, emitStartLog = false)
        val fields = argumentCaptor<InternalFieldsMap>()
        span.end(SpanResult.SUCCESS, endTimeMs = 1337)
        verify(logger, times(1)).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            fields.capture(),
            eq(null),
            eq(null),
            eq(false),
            any(),
        )

        assertThat(fields.firstValue).containsKey("_span_id")
        assertThat(fields.firstValue).containsEntry("_duration_ms", "1337".toFieldValue())
    }
}
