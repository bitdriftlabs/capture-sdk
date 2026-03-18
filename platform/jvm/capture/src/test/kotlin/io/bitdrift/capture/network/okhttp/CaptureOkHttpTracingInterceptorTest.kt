// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.Capture
import io.bitdrift.capture.IRuntimeProvider
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.LoggerState
import io.bitdrift.capture.common.RuntimeStringConfig
import io.bitdrift.capture.fakes.FakeOkHttpInterceptorChain
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicReference

class CaptureOkHttpTracingInterceptorTest {
    private val runtimeProvider: IRuntimeProvider = mock()

    @After
    fun tearDown() {
        Capture.Logger.resetShared()
    }

    @Test
    fun intercept_whenActiveTracingAndW3c_shouldAddExpectedHeaders() {
        setActiveTracingState(isActiveTracingEnabled = true)
        setPropagationMode("w3c")
        val interceptor = CaptureOkHttpTracingInterceptor(runtimeProvider)
        val chain = FakeOkHttpInterceptorChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        val traceparent = request.header("traceparent")
        val traceId = request.header(CaptureOkHttpTracingInterceptor.TRACE_ID_HEADER)
        assertThat(traceparent).startsWith("00-")
        assertThat(traceparent).endsWith("-01")
        assertThat(traceId).hasSize(32)
        assertThat(request.header("b3")).isNull()
    }

    @Test
    fun intercept_whenActiveTracingAndB3_shouldAddExpectedHeaders() {
        setActiveTracingState(isActiveTracingEnabled = true)
        setPropagationMode("b3-single")

        val interceptor = CaptureOkHttpTracingInterceptor(runtimeProvider)
        val chain = FakeOkHttpInterceptorChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        val b3 = request.header("b3")
        val traceId = request.header(CaptureOkHttpTracingInterceptor.TRACE_ID_HEADER)
        assertThat(b3).contains("-1")
        assertThat(traceId).hasSize(32)
        assertThat(request.header("traceparent")).isNull()
    }

    @Test
    fun intercept_whenActiveTracingAndB3Multi_shouldAddExpectedHeaders() {
        setActiveTracingState(isActiveTracingEnabled = true)
        setPropagationMode("b3-multi")

        val interceptor = CaptureOkHttpTracingInterceptor(runtimeProvider)
        val chain = FakeOkHttpInterceptorChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        assertThat(request.header("X-B3-TraceId")).hasSize(32)
        assertThat(request.header("X-B3-SpanId")).hasSize(16)
        assertThat(request.header("X-B3-Sampled")).isEqualTo("1")
        assertThat(request.header("traceparent")).isNull()
        assertThat(request.header("b3")).isNull()
        assertThat(request.header(CaptureOkHttpTracingInterceptor.TRACE_ID_HEADER)).hasSize(32)
    }

    @Test
    fun intercept_whenNotActiveTracing_debugBypassStillAddsHeaders() {
        setActiveTracingState(isActiveTracingEnabled = false)
        setPropagationMode("w3c")

        val interceptor = CaptureOkHttpTracingInterceptor(runtimeProvider)
        val chain = FakeOkHttpInterceptorChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        assertThat(request.header("traceparent")).isNull()
        assertThat(request.header("b3")).isNull()
        assertThat(request.header(CaptureOkHttpTracingInterceptor.TRACE_ID_HEADER)).isNull()
    }

    @Test
    fun intercept_whenPropagationModeOff_shouldNotAddHeaders() {
        setActiveTracingState(isActiveTracingEnabled = true)
        setPropagationMode("off")

        val interceptor = CaptureOkHttpTracingInterceptor(runtimeProvider)
        val chain = FakeOkHttpInterceptorChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        assertThat(request.header("traceparent")).isNull()
        assertThat(request.header("b3")).isNull()
        assertThat(request.header(CaptureOkHttpTracingInterceptor.TRACE_ID_HEADER)).isNull()
    }

    @Test
    fun intercept_whenStringModeUnknown_shouldFallbackToIntMode() {
        setActiveTracingState(isActiveTracingEnabled = true)
        setPropagationMode("invalid")

        val interceptor = CaptureOkHttpTracingInterceptor(runtimeProvider)
        val chain = FakeOkHttpInterceptorChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        assertThat(request.header("b3")).isNull()
        assertThat(request.header("traceparent")).isNull()
        assertThat(request.header(CaptureOkHttpTracingInterceptor.TRACE_ID_HEADER)).isNull()
    }

    private fun setPropagationMode(value: String) {
        whenever(runtimeProvider.getRuntimeStringConfigValue(RuntimeStringConfig.TRACE_PROPAGATION_MODE)).thenReturn(value)
    }

    private fun setActiveTracingState(isActiveTracingEnabled: Boolean) {
        val logger: LoggerImpl = mock()
        whenever(logger.isTracingActive).thenReturn(isActiveTracingEnabled)

        val field = Capture::class.java.getDeclaredField("default")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val stateRef = field.get(Capture) as AtomicReference<LoggerState>
        stateRef.set(LoggerState.Started(logger))
    }
}
