// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.UUID

class HttpResponseInfoTest {
    @Test
    fun testHTTPResponseTemplateOverride() {
        val spanId = UUID.randomUUID()
        val requestInfo =
            HttpRequestInfo(
                host = "foo.com",
                method = "GET",
                path = HttpUrlPath("/my_path/12345", "/template/<id>"),
                query = "my=query",
                headers = mapOf("content-type" to "json"),
                spanId = spanId,
                extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
            )

        val responseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response =
                    HttpResponse(
                        result = HttpResponse.HttpResult.SUCCESS,
                        path = HttpUrlPath("/foo_path/12345", "/template/<id>"),
                        error = RuntimeException("my_error"),
                        headers = mapOf("response_header" to "response_value"),
                    ),
                durationMs = 60L,
                extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
            )

        assertThat(responseInfo.fields)
            .hasEntrySatisfying("_host") { assertThat(it.toString()).isEqualTo("foo.com") }
            .hasEntrySatisfying("_method") { assertThat(it.toString()).isEqualTo("GET") }
            .hasEntrySatisfying("_path") { assertThat(it.toString()).isEqualTo("/foo_path/12345") }
            .hasEntrySatisfying("_path_template") { assertThat(it.toString()).isEqualTo("/template/<id>") }
            .hasEntrySatisfying("_span_type") { assertThat(it.toString()).isEqualTo("end") }
            .hasEntrySatisfying("_result") { assertThat(it.toString()).isEqualTo("success") }
    }

    @Test
    fun testHTTPRequestExplicitPathTemplate() {
        val spanId = UUID.randomUUID()
        val requestInfo =
            HttpRequestInfo(
                host = "foo.com",
                method = "GET",
                path = HttpUrlPath("/my_path/12345", "/template/<id>"),
                query = "my=query",
                headers = mapOf("content-type" to "json"),
                spanId = spanId,
                extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
            )

        val responseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response =
                    HttpResponse(
                        result = HttpResponse.HttpResult.SUCCESS,
                        path = HttpUrlPath("/my_path/12345"),
                        error = RuntimeException("my_error"),
                        headers = mapOf("response_header" to "response_value"),
                    ),
                durationMs = 60L,
                extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
            )

        assertThat(responseInfo.fields)
            .hasEntrySatisfying("_path") { assertThat(it.toString()).isEqualTo("/my_path/12345") }
            .hasEntrySatisfying("_path_template") { assertThat(it.toString()).isEqualTo("/template/<id>") }
    }

    @Test
    fun testHTTPResponseRequestAttributesOverride() {
        val spanId = UUID.randomUUID()

        val requestInfo =
            HttpRequestInfo(
                host = "api.bitdrift.io",
                method = "GET",
                path = HttpUrlPath("/my_path/12345"),
                query = "my=query",
                spanId = spanId,
                extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
            )

        assertThat(requestInfo.fields)
            .hasEntrySatisfying("_host") { assertThat(it.toString()).isEqualTo("api.bitdrift.io") }
            .hasEntrySatisfying("_method") { assertThat(it.toString()).isEqualTo("GET") }
            .hasEntrySatisfying("_span_type") { assertThat(it.toString()).isEqualTo("start") }

        val responseInfo =
            HttpResponseInfo(
                request = requestInfo,
                response =
                    HttpResponse(
                        host = "foo.com",
                        path = HttpUrlPath("/foo_path/12345"),
                        query = "foo_query",
                        result = HttpResponse.HttpResult.SUCCESS,
                        error = RuntimeException("my_error"),
                    ),
                durationMs = 60L,
                extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
            )

        assertThat(responseInfo.fields)
            .hasEntrySatisfying("_host") { assertThat(it.toString()).isEqualTo("foo.com") }
            .hasEntrySatisfying("_path") { assertThat(it.toString()).isEqualTo("/foo_path/12345") }
            .hasEntrySatisfying("_query") { assertThat(it.toString()).isEqualTo("foo_query") }
            .hasEntrySatisfying("_span_type") { assertThat(it.toString()).isEqualTo("end") }
            .hasEntrySatisfying("_result") { assertThat(it.toString()).isEqualTo("success") }

        assertThat(responseInfo.matchingFields)
            .hasEntrySatisfying("_request._host") { assertThat(it.toString()).isEqualTo("api.bitdrift.io") }
            .hasEntrySatisfying("_request._method") { assertThat(it.toString()).isEqualTo("GET") }
            .hasEntrySatisfying("_request._span_type") { assertThat(it.toString()).isEqualTo("start") }
    }
}
