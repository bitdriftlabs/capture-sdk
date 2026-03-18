// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ILogger
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CaptureOkHttpTracingInterceptorTest {
    @Test
    fun injectsW3CHeadersWhenTracingIsActive() {
        val logger: ILogger = mock()
        whenever(logger.isTracingActive).thenReturn(true)

        val interceptor = CaptureOkHttpTracingInterceptor(logger, CaptureOkHttpTracingInterceptor.HeaderFormat.W3C)
        val chain = CapturingChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        val traceparent = request.header("traceparent")
        val traceId = request.header("x-capture-span-trace-field-trace_id")

        assertThat(traceparent).startsWith("00-")
        assertThat(traceparent).endsWith("-01")
        assertThat(traceId).hasSize(32)
        assertThat(request.header("b3")).isNull()
    }

    @Test
    fun injectsB3HeadersWhenTracingIsActive() {
        val logger: ILogger = mock()
        whenever(logger.isTracingActive).thenReturn(true)

        val interceptor = CaptureOkHttpTracingInterceptor(logger, CaptureOkHttpTracingInterceptor.HeaderFormat.B3)
        val chain = CapturingChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        val b3 = request.header("b3")
        val traceId = request.header("x-capture-span-trace-field-trace_id")

        assertThat(b3).contains("-1")
        assertThat(traceId).hasSize(32)
        assertThat(request.header("traceparent")).isNull()
    }

    @Test
    fun skipsInjectionWhenTracingIsInactive() {
        val logger: ILogger = mock()
        whenever(logger.isTracingActive).thenReturn(false)

        val interceptor = CaptureOkHttpTracingInterceptor(logger, CaptureOkHttpTracingInterceptor.HeaderFormat.W3C)
        val chain = CapturingChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        val request = chain.capturedRequest!!
        assertThat(request.header("traceparent")).isNull()
        assertThat(request.header("b3")).isNull()
        assertThat(request.header("x-capture-span-key")).isNull()
        assertThat(request.header("x-capture-span-trace-field-trace_id")).isNull()
    }

    private class CapturingChain(
        private val originalRequest: Request,
    ) : Interceptor.Chain {
        var capturedRequest: Request? = null

        override fun request(): Request = originalRequest

        override fun proceed(request: Request): Response {
            capturedRequest = request
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        override fun connection() = null

        override fun call() = throw UnsupportedOperationException()

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(
            timeout: Int,
            unit: java.util.concurrent.TimeUnit,
        ): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(
            timeout: Int,
            unit: java.util.concurrent.TimeUnit,
        ): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(
            timeout: Int,
            unit: java.util.concurrent.TimeUnit,
        ): Interceptor.Chain = this
    }
}
