// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TracePropagationTest {
    @Test
    fun hasExistingTraceHeaders_detectsB3Single() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("b3", "abc-def-0")
                .build()
        assertThat(TracePropagation.hasExistingTraceHeaders(request)).isTrue()
    }

    @Test
    fun hasExistingTraceHeaders_detectsB3Multi() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("X-B3-TraceId", "abc")
                .build()
        assertThat(TracePropagation.hasExistingTraceHeaders(request)).isTrue()
    }

    @Test
    fun hasExistingTraceHeaders_returnsFalseWhenNoHeaders() {
        val request = Request.Builder().url("https://example.com").build()
        assertThat(TracePropagation.hasExistingTraceHeaders(request)).isFalse()
    }

    @Test
    fun extractSampledTraceId_w3cSampled_returnsTraceId() {
        val traceId = "88c131f5a4a41657a4cc039862759571"
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("traceparent", "00-$traceId-639ec5ab80cb312d-01")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isEqualTo(traceId)
    }

    @Test
    fun extractSampledTraceId_w3cNotSampled_returnsNull() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("traceparent", "00-88c131f5a4a41657a4cc039862759571-639ec5ab80cb312d-00")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }

    @Test
    fun extractSampledTraceId_w3cSampledWithExtraBits_returnsTraceId() {
        val traceId = "88c131f5a4a41657a4cc039862759571"
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("traceparent", "00-$traceId-639ec5ab80cb312d-03")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isEqualTo(traceId)
    }

    @Test
    fun extractSampledTraceId_w3cSampledFf_returnsTraceId() {
        val traceId = "88c131f5a4a41657a4cc039862759571"
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("traceparent", "00-$traceId-639ec5ab80cb312d-ff")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isEqualTo(traceId)
    }

    @Test
    fun extractSampledTraceId_w3cNotSampledEvenFlags_returnsNull() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("traceparent", "00-88c131f5a4a41657a4cc039862759571-639ec5ab80cb312d-02")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }

    @Test
    fun extractSampledTraceId_w3cGarbageFlags_returnsNull() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("traceparent", "00-88c131f5a4a41657a4cc039862759571-639ec5ab80cb312d-zz")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }

    @Test
    fun extractSampledTraceId_b3SingleSampled_returnsTraceId() {
        val traceId = "88c131f5a4a41657a4cc039862759571"
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("b3", "$traceId-639ec5ab80cb312d-1")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isEqualTo(traceId)
    }

    @Test
    fun extractSampledTraceId_b3SingleDebug_returnsTraceId() {
        val traceId = "88c131f5a4a41657a4cc039862759571"
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("b3", "$traceId-639ec5ab80cb312d-d")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isEqualTo(traceId)
    }

    @Test
    fun extractSampledTraceId_b3SingleNotSampled_returnsNull() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("b3", "88c131f5a4a41657a4cc039862759571-639ec5ab80cb312d-0")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }

    @Test
    fun extractSampledTraceId_b3MultiSampled_returnsTraceId() {
        val traceId = "88c131f5a4a41657a4cc039862759571"
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("X-B3-TraceId", traceId)
                .header("X-B3-Sampled", "1")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isEqualTo(traceId)
    }

    @Test
    fun extractSampledTraceId_b3MultiNotSampled_returnsNull() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("X-B3-TraceId", "88c131f5a4a41657a4cc039862759571")
                .header("X-B3-Sampled", "0")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }

    @Test
    fun extractSampledTraceId_b3MultiMissingSampled_returnsNull() {
        val request =
            Request
                .Builder()
                .url("https://example.com")
                .header("X-B3-TraceId", "88c131f5a4a41657a4cc039862759571")
                .build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }

    @Test
    fun extractSampledTraceId_noHeaders_returnsNull() {
        val request = Request.Builder().url("https://example.com").build()
        assertThat(TracePropagation.extractSampledTraceId(request)).isNull()
    }
}
