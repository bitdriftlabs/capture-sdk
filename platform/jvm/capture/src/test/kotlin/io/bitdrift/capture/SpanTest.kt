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
}
