// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.ILogger
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponseInfo
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Proxy

class CaptureOkHttpEventListenerFactoryTest {
    private val endpoint = "https://api.bitdrift.io/my_path/12345?my_query=my_value"
    private val clock: IClock = mock()
    private val logger: ILogger = mock()

    private val call: Call = mock()

    @Test
    fun testRequestAndResponseReuseCommonInfo() {
        // ARRANGE
        val callStartTimeMs = 100L
        val dnsStartTimeMs = 110L
        val dnsEndTimeMs = 115L
        val connectStartTimeMs = 120L
        val tlsStartTimeMs = 125L
        val tlsEndTimeMs = 130L
        val connectEndTimeMs = 135L
        val requestHeadersEndTimeMs = 140L
        val requestBodyEndTimeMs = 145L
        val responseHeadersStartTimeMs = 150L
        val callEndtimeMs = 155L

        val callDurationMs = callEndtimeMs - callStartTimeMs
        val dnsDurationMs = dnsEndTimeMs - dnsStartTimeMs
        val tlsDurationMs = tlsEndTimeMs - tlsStartTimeMs
        val tcpDurationMs = tlsStartTimeMs - connectStartTimeMs
        val fetchInitDurationMs = dnsStartTimeMs - callStartTimeMs
        val responseLatencyMs = responseHeadersStartTimeMs - requestBodyEndTimeMs
        whenever(clock.elapsedRealtime()).thenReturn(
            callStartTimeMs,
            dnsStartTimeMs,
            dnsEndTimeMs,
            connectStartTimeMs,
            tlsStartTimeMs,
            tlsEndTimeMs,
            connectEndTimeMs,
            requestHeadersEndTimeMs,
            requestBodyEndTimeMs,
            responseHeadersStartTimeMs,
            callEndtimeMs,
        )

        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .header("foo", "bar")
                .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("message")
                .header("response_header", "response_header_value")
                .protocol(Protocol.HTTP_1_1)
                .build()

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.dnsStart(call, "foo.com")
        listener.dnsEnd(call, "foo.com", listOf())

        listener.connectStart(call, InetSocketAddress.createUnresolved("foo.com", 443), Proxy.NO_PROXY)

        listener.secureConnectStart(call)
        listener.secureConnectEnd(call, handshake = null)

        listener.connectEnd(call, InetSocketAddress.createUnresolved("foo.com", 443), Proxy.NO_PROXY, null)

        listener.requestHeadersEnd(call, request)
        listener.requestBodyEnd(call, 4)

        listener.responseHeadersStart(call)
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
        assertThat(httpRequestInfo.fields["_span_name"].toString()).isEqualTo("_http")
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
        assertThat(httpResponseInfo.fields["_tls_duration_ms"].toString()).isEqualTo(tlsDurationMs.toString())
        assertThat(httpResponseInfo.fields["_tcp_duration_ms"].toString()).isEqualTo(tcpDurationMs.toString())
        assertThat(httpResponseInfo.fields["_fetch_init_duration_ms"].toString()).isEqualTo(fetchInitDurationMs.toString())
        assertThat(httpResponseInfo.fields["_response_latency_ms"].toString()).isEqualTo(responseLatencyMs.toString())
        assertThat(httpResponseInfo.fields["_protocol"].toString()).isEqualTo("http/1.1")

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

        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .header("foo", "bar")
                .header("x-capture-path-template", "/foo/<id>")
                .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response =
            Response
                .Builder()
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

        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .header("foo", "bar")
                .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response =
            Response
                .Builder()
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
        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .header("foo", "bar")
                .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response =
            Response
                .Builder()
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
        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .header("foo", "bar")
                .build()
        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val response =
            Response
                .Builder()
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

    @Test
    fun testExtraHeadersSendCustomSpans() {
        // ARRANGE
        val headerFields =
            mapOf(
                "x-capture-span-key" to "gql",
                "x-capture-span-gql-name" to "mySpanName",
                "x-capture-span-gql-field-operation-name" to "myOperationName",
                "x-capture-span-gql-field-operation-id" to "myOperationId",
                "x-capture-span-gql-field-operation-type" to "query",
                "x-capture-path-template" to "gql-myOperationName",
            )
        val expectedSpanName = "_mySpanName"
        val expectedFields =
            mapOf(
                "_operation_name" to "myOperationName",
                "_operation_id" to "myOperationId",
                "_operation_type" to "query",
                "_path_template" to "gql-myOperationName",
            )

        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .headers(headerFields.toHeaders())
                .build()

        val response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("message")
                .header("response_header", "response_header_value")
                .build()

        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

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

        assertThat(httpRequestInfo.fields["_span_name"].toString()).isEqualTo(expectedSpanName)
        // validate all the extra headers are present as properly formatted fields
        assertThat(
            httpRequestInfo.fields
                .mapValues { it.value.toString() }
                .entries
                .containsAll(expectedFields.entries),
        ).isTrue()
        // validate all request fields are present in response
        assertThat(
            httpResponseInfo.fields
                .mapValues { it.value.toString() }
                .entries
                .containsAll(expectedFields.entries),
        ).isTrue()
    }

    @Test
    fun testApolloHeadersSendGraphQlSpans() {
        // ARRANGE
        val headerFields =
            mapOf(
                "X-APOLLO-OPERATION-NAME" to "myOperationName",
                "X-APOLLO-OPERATION-ID" to "myOperationId",
                "X-APOLLO-OPERATION-TYPE" to "query",
            )
        val expectedSpanName = "_graphql"
        val expectedFields =
            mapOf(
                "_operation_name" to "myOperationName",
                "_operation_id" to "myOperationId",
                "_operation_type" to "query",
                "_path_template" to "gql-myOperationName",
            )

        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .headers(headerFields.toHeaders())
                .build()

        val response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("message")
                .header("response_header", "response_header_value")
                .build()

        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock)
        val listener = factory.create(call)

        listener.callStart(call)

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

        assertThat(httpRequestInfo.fields["_span_name"].toString()).isEqualTo(expectedSpanName)
        // validate all the extra headers are present as properly formatted fields
        assertThat(
            httpRequestInfo.fields
                .mapValues { it.value.toString() }
                .entries
                .containsAll(expectedFields.entries),
        ).isTrue()
        // validate all request fields are present in response
        assertThat(
            httpResponseInfo.fields
                .mapValues { it.value.toString() }
                .entries
                .containsAll(expectedFields.entries),
        ).isTrue()
    }

    @Test
    fun testCustomFieldProviderAddsExtraFields() {
        // ARRANGE
        val requestMetadata = "1234"

        val request =
            Request
                .Builder()
                .url(endpoint)
                .post("test".toRequestBody())
                .tag(requestMetadata)
                .build()

        val response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("message")
                .header("response_header", "response_header_value")
                .build()

        val call: Call = mock()
        whenever(call.request()).thenReturn(request)

        val extraFieldProvider =
            OkHttpRequestFieldProvider {
                mapOf("requestMetadata" to it.tag() as String)
            }

        // ACT
        val factory = CaptureOkHttpEventListenerFactory(null, logger, clock, extraFieldProvider)
        val listener = factory.create(call)

        listener.callStart(call)

        listener.responseHeadersEnd(call, response)
        listener.responseBodyEnd(call, 234)

        listener.callEnd(call)

        // ASSERT
        val httpRequestInfoCapture = argumentCaptor<HttpRequestInfo>()
        verify(logger).log(httpRequestInfoCapture.capture())
        val httpResponseInfoCapture = argumentCaptor<HttpResponseInfo>()
        verify(logger).log(httpResponseInfoCapture.capture())

        val httpRequestInfo = httpRequestInfoCapture.firstValue

        assertThat(httpRequestInfo.fields["requestMetadata"].toString()).isEqualTo(requestMetadata)
    }

    @Test
    fun create_withNullLoggerAndNullTargetListener_whenNoLoggerAndNoTarget() {
        val factory =
            CaptureOkHttpEventListenerFactory(
                targetEventListenerCreator = null,
                logger = null,
                clock = clock,
            )

        val noOpEventListener = factory.create(call)

        assertThat(noOpEventListener).isInstanceOf(CaptureOkHttpEventListenerFactory.NoOpEventListener::class.java)
    }

    @Test
    fun create_withNullLogAndValidTargetListener_returnsTargetListener() {
        val targetEventListener: EventListener = mock()
        val factory =
            CaptureOkHttpEventListenerFactory(
                targetEventListenerCreator = { targetEventListener },
                logger = null,
                clock = clock,
            )

        val listener = factory.create(call)

        assertThat(listener).isSameAs(targetEventListener)
    }
}
