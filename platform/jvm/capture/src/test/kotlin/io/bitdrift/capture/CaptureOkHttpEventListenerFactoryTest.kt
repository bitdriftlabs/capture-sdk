// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.FileNotFoundException
import java.io.InterruptedIOException

class CaptureOkHttpEventListenerFactoryTest {
    private val endpoint = "https://api.bitdrift.io/my_path/12345?my_query=my_value"
    private val clock: IClock = mock()
    private val logger: ILogger = mock()

    init {
        CaptureJniLibrary.load()
    }

    @Test
    fun testRequestAndResponseReuseCommonInfo() {
        // ARRANGE
        val requestTimeMs = 100L
        val dnsStartTimeMs = 110L
        val dnsEndTimeMs = 115L
        val responseTimeMs = 150L

        val callDurationMs = responseTimeMs - requestTimeMs
        val dnsDurationMs = dnsEndTimeMs - dnsStartTimeMs
        whenever(clock.elapsedRealtime()).thenReturn(requestTimeMs, dnsStartTimeMs, dnsEndTimeMs, responseTimeMs)

        val request = Request.Builder()
            .url(endpoint)
            .post("test".toRequestBody())
            .header("foo", "bar")
            .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("message")
            .header("response_header", "response_header_value")
            .build()

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.dnsStart(call, "foo.com")
        listener.dnsEnd(call, "foo.com", listOf())

        listener.requestHeadersEnd(call, request)
        listener.requestBodyEnd(call, 4)

        listener.responseHeadersEnd(call, response)
        listener.responseBodyEnd(call, 234)

        listener.callEnd(call)

        // ASSERT
        val httpRequestInfoCapture = argumentCaptor<HttpRequestInfo>()
        verify(logger).log(httpRequestInfoCapture.capture())
        val httpResponseInfoCapture = argumentCaptor<HttpResponseInfo>()
        verify(logger).log(httpResponseInfoCapture.capture())

        val httpRequestInfo = httpRequestInfoCapture.firstValue
        val httpResponseInfo = httpResponseInfoCapture.firstValue
        // common request fields
        assertThat(httpRequestInfo.fields["_host"].toString()).isEqualTo("api.bitdrift.io")
        assertThat(httpRequestInfo.fields["_host"].toString())
            .isEqualTo(httpResponseInfo.fields["_host"].toString())
        assertThat(httpRequestInfo.fields["_method"].toString()).isEqualTo("POST")
        assertThat(httpResponseInfo.fields["_method"].toString())
            .isEqualTo(httpRequestInfo.fields["_method"].toString())
        assertThat(httpRequestInfo.fields["_path"].toString()).isEqualTo("/my_path/12345")
        assertThat(httpResponseInfo.fields["_path"].toString())
            .isEqualTo(httpRequestInfo.fields["_path"].toString())
        assertThat(httpRequestInfo.fields["_query"].toString()).isEqualTo("my_query=my_value")
        assertThat(httpResponseInfo.fields["_query"].toString())
            .isEqualTo(httpRequestInfo.fields["_query"].toString())
        assertThat(httpResponseInfo.fields["_span_id"].toString())
            .isEqualTo(httpRequestInfo.fields["_span_id"].toString())
        // request-only fields
        assertThat(httpRequestInfo.fields["_request_body_bytes_expected_to_send_count"].toString()).isEqualTo("4")
        // request matching fields
        assertThat(httpRequestInfo.matchingFields["_headers.foo"].toString()).isEqualTo("bar")
        // response-only fields
        assertThat(httpResponseInfo.fields["_request_body_bytes_expected_to_send_count"]).isNull()
        assertThat(httpResponseInfo.fields["_result"].toString()).isEqualTo("success")
        assertThat(httpResponseInfo.fields["_status_code"].toString()).isEqualTo("200")
        assertThat(httpResponseInfo.fields["_duration_ms"].toString()).isEqualTo(callDurationMs.toString())
        assertThat(httpResponseInfo.fields["_dns_resolution_duration_ms"].toString()).isEqualTo(dnsDurationMs.toString())

        assertThat(httpResponseInfo.fields["_request_body_bytes_sent_count"].toString()).isEqualTo("4")
        assertThat(httpResponseInfo.fields["_response_body_bytes_received_count"].toString()).isEqualTo("234")

        assertThat(httpResponseInfo.fields["_request_headers_bytes_count"].toString()).isEqualTo("10")
        assertThat(httpResponseInfo.fields["_response_headers_bytes_count"].toString()).isEqualTo("40")
        // response matching fields
        assertThat(httpResponseInfo.matchingFields["_request._headers.foo"].toString()).isEqualTo("bar")
        assertThat(httpResponseInfo.matchingFields["_headers.response_header"].toString()).isEqualTo("response_header_value")
    }

    @Test
    fun testPathTemplateOverride() {
        // ARRANGE
        val requestTimeMs = 100L
        val responseTimeMs = 150L
        whenever(clock.elapsedRealtime()).thenReturn(requestTimeMs, responseTimeMs)

        val request = Request.Builder()
            .url(endpoint)
            .post("test".toRequestBody())
            .header("foo", "bar")
            .header("x-capture-path-template", "/foo/<id>")
            .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("message")
            .header("response_header", "response_header_value")
            .build()

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.dnsStart(call, "foo.com")
        listener.dnsEnd(call, "foo.com", listOf())

        listener.requestHeadersEnd(call, request)
        listener.requestBodyEnd(call, 4)

        listener.responseHeadersEnd(call, response)
        listener.responseBodyEnd(call, 234)

        listener.callEnd(call)

        // ASSERT

        val httpRequestInfoCapture = argumentCaptor<HttpRequestInfo>()
        verify(logger).log(httpRequestInfoCapture.capture())
        val httpResponseInfoCapture = argumentCaptor<HttpResponseInfo>()
        verify(logger).log(httpResponseInfoCapture.capture())

        val httpRequestInfo = httpRequestInfoCapture.firstValue
        val httpResponseInfo = httpResponseInfoCapture.firstValue

        assertThat(httpRequestInfo.fields["_path"].toString()).isEqualTo("/my_path/12345")
        assertThat(httpResponseInfo.fields["_path"].toString())
            .isEqualTo(httpRequestInfo.fields["_path"].toString())
        assertThat(httpRequestInfo.fields["_path_template"].toString()).isEqualTo("/foo/<id>")
        assertThat(httpResponseInfo.fields["_path_template"].toString())
            .isEqualTo(httpRequestInfo.fields["_path_template"].toString())
    }

    @Test
    fun testSuccessfulResponseWithNoDNSResolution() {
        // ARRANGE
        val requestTimeMs = 100L
        val responseTimeMs = 150L

        val callDurationMs = responseTimeMs - requestTimeMs

        whenever(clock.elapsedRealtime()).thenReturn(
            requestTimeMs,
            responseTimeMs,
        )

        val request = Request.Builder()
            .url(endpoint)
            .post("test".toRequestBody())
            .header("foo", "bar")
            .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("message")
            .header("response_header", "response_header_value")
            .build()

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.requestHeadersEnd(call, request)
        listener.requestBodyEnd(call, 4)

        listener.responseHeadersEnd(call, response)
        listener.responseBodyEnd(call, 234)

        listener.callEnd(call)

        // ASSERT
        val httpRequestInfoCapture = argumentCaptor<HttpRequestInfo>()
        verify(logger).log(httpRequestInfoCapture.capture())
        val httpResponseInfoCapture = argumentCaptor<HttpResponseInfo>()
        verify(logger).log(httpResponseInfoCapture.capture())

        val httpRequestInfo = httpRequestInfoCapture.firstValue
        val httpResponseInfo = httpResponseInfoCapture.firstValue

        assertThat(httpRequestInfo.fields["_request_body_bytes_expected_to_send_count"].toString()).isEqualTo("4")
        assertThat(httpResponseInfo.fields["_duration_ms"].toString()).isEqualTo(callDurationMs.toString())
        assertThat(httpResponseInfo.fields["_dns_resolution_duration_ms"]).isNull()

        assertThat(httpResponseInfo.fields["_request_body_bytes_sent_count"].toString()).isEqualTo("4")
        assertThat(httpResponseInfo.fields["_response_body_bytes_received_count"].toString()).isEqualTo("234")
        assertThat(httpResponseInfo.fields["_request_headers_bytes_count"].toString()).isEqualTo("10")
        assertThat(httpResponseInfo.fields["_response_headers_bytes_count"].toString()).isEqualTo("40")
    }

    @Test
    fun testRequestAndErrorThrown() {
        // ARRANGE
        val request = Request.Builder()
            .url(endpoint)
            .post("test".toRequestBody())
            .header("foo", "bar")
            .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("message")
            .header("response_header", "response_header_value")
            .build()

        val errorMessage = "test error"
        val err = FileNotFoundException(errorMessage)

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.requestHeadersEnd(call, request)
        listener.requestBodyEnd(call, 4)

        listener.responseHeadersEnd(call, response)
        listener.responseBodyEnd(call, 234)

        listener.callFailed(call, err)

        // ASSERT
        val httpResponseInfoCapture = argumentCaptor<HttpResponseInfo>()
        verify(logger).log(any<HttpRequestInfo>())
        verify(logger).log(httpResponseInfoCapture.capture())

        val httpResponseInfo = httpResponseInfoCapture.firstValue

        assertThat(httpResponseInfo.fields["_result"].toString()).isEqualTo("failure")
        assertThat(httpResponseInfo.fields["_error_type"].toString()).isEqualTo(err::javaClass.get().simpleName)
        assertThat(httpResponseInfo.fields["_error_message"].toString()).isEqualTo(errorMessage)
    }

    @Test
    fun testRequestAndErrorThrownCanceled() {
        // ARRANGE
        val request = Request.Builder()
            .url(endpoint)
            .post("test".toRequestBody())
            .header("foo", "bar")
            .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("message")
            .header("response_header", "response_header_value")
            .build()

        val errorMessage = "test error"
        val err = InterruptedIOException(errorMessage)

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.requestHeadersEnd(call, request)
        listener.requestBodyEnd(call, 4)

        listener.responseHeadersEnd(call, response)
        listener.responseBodyEnd(call, 234)

        listener.callFailed(call, err)

        // ASSERT
        val httpResponseInfoCapture = argumentCaptor<HttpResponseInfo>()
        verify(logger).log(any<HttpRequestInfo>())
        verify(logger).log(httpResponseInfoCapture.capture())

        val httpResponseInfo = httpResponseInfoCapture.firstValue

        assertThat(httpResponseInfo.fields["_result"].toString()).isEqualTo("canceled")
        assertThat(httpResponseInfo.fields["_error_type"].toString()).isEqualTo(err::javaClass.get().simpleName)
        assertThat(httpResponseInfo.fields["_error_message"].toString()).isEqualTo(errorMessage)
    }
}
