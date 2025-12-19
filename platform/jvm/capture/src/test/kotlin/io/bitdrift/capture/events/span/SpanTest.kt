// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.span

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.LogAttributesOverrides
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.utils.toStringMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SpanTest {
    private val logger: LoggerImpl = mock()
    private val clock: IClock = mock()

    @Test
    fun logs() {
        val span = Span(logger, "name", LogLevel.INFO, clock = clock)

        val arrayFields = argumentCaptor<ArrayFields>()
        span.end(SpanResult.SUCCESS)

        verify(logger, times(2)).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFields.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            any(),
        )

        // start
        val startFields = arrayFields.firstValue.toStringMap()
        assertThat(startFields).containsKey("_span_id")
        assertThat(startFields).containsEntry("_span_name", "name")
        assertThat(startFields).containsEntry("_span_type", "start")
        // end
        val endFields = arrayFields.secondValue.toStringMap()
        assertThat(endFields).containsKey("_span_id")
        assertThat(endFields).containsEntry("_span_name", "name")
        assertThat(endFields).containsEntry("_span_type", "end")
        assertThat(endFields).containsKey("_duration_ms")
        assertThat(endFields).containsEntry("_result", "success")
    }

    @Test
    fun spansWithStartAndEnd() {
        val span = Span(logger, "name", LogLevel.INFO, clock = clock, customStartTimeMs = 1L)

        val arrayFields = argumentCaptor<ArrayFields>()
        span.end(SpanResult.SUCCESS, endTimeMs = 1000L)

        verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFields.capture(),
            eq(ArrayFields.EMPTY),
            eq(LogAttributesOverrides.OccurredAt(1)),
            eq(false),
            any(),
        )
        verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFields.capture(),
            eq(ArrayFields.EMPTY),
            eq(LogAttributesOverrides.OccurredAt(1000)),
            eq(false),
            any(),
        )

        // start
        val startFields = arrayFields.firstValue.toStringMap()
        assertThat(startFields).containsKey("_span_id")
        assertThat(startFields).containsEntry("_span_name", "name")
        assertThat(startFields).containsEntry("_span_type", "start")
        // end
        val endFields = arrayFields.secondValue.toStringMap()
        assertThat(endFields).containsKey("_span_id")
        assertThat(endFields).containsEntry("_duration_ms", "999")
    }

    @Test
    fun spansWithStartAndNoEnd() {
        whenever(clock.elapsedRealtime()).thenReturn(0L)
        val spanWithNoEnd = Span(logger, "name", LogLevel.INFO, clock = clock, customStartTimeMs = 1L)
        val arrayFieldsWithNoEnd = argumentCaptor<ArrayFields>()
        whenever(clock.elapsedRealtime()).thenReturn(1337L)
        spanWithNoEnd.end(SpanResult.SUCCESS)

        verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFieldsWithNoEnd.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            any(),
        )

        verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFieldsWithNoEnd.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            any(),
        )

        val endFields = arrayFieldsWithNoEnd.secondValue.toStringMap()
        assertThat(endFields).containsKey("_span_id")
        assertThat(endFields).containsEntry("_duration_ms", "1337")
    }

    @Test
    fun spansWithNoStartAndEnd() {
        whenever(clock.elapsedRealtime()).thenReturn(1337L)
        val spanWithNoStart = Span(logger, "name", LogLevel.INFO, clock = clock)
        val arrayFieldsWithNoStart = argumentCaptor<ArrayFields>()
        whenever(clock.elapsedRealtime()).thenReturn(1338L)
        spanWithNoStart.end(SpanResult.SUCCESS, endTimeMs = 1337)

        verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFieldsWithNoStart.capture(),
            eq(ArrayFields.EMPTY),
            eq(null),
            eq(false),
            any(),
        )

        verify(logger).log(
            eq(LogType.SPAN),
            eq(LogLevel.INFO),
            arrayFieldsWithNoStart.capture(),
            eq(ArrayFields.EMPTY),
            eq(LogAttributesOverrides.OccurredAt(1337)),
            eq(false),
            any(),
        )

        val endFields = arrayFieldsWithNoStart.secondValue.toStringMap()
        assertThat(endFields).containsKey("_span_id")
        assertThat(endFields).containsEntry("_duration_ms", "1")
    }
}
