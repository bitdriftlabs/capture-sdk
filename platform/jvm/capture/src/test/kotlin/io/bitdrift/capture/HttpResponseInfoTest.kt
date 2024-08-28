// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.network.HttpRequestInfo
import io.bitdrift.capture.network.HttpResponse
import io.bitdrift.capture.network.HttpResponseInfo
import io.bitdrift.capture.network.HttpUrlPath
import io.bitdrift.capture.providers.toFields
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.UUID

class HttpResponseInfoTest {

    @Test
    fun test_response_template_override() {
        val spanId = UUID.randomUUID()
        val requestInfo = HttpRequestInfo(
            host = "foo.com",
            method = "GET",
            path = HttpUrlPath("/my_path/12345", "/template/<id>"),
            query = "my=query",
            headers = mapOf("content-type" to "json"),
            spanId = spanId,
            extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
        )

        val responseInfo = HttpResponseInfo(
            request = requestInfo,
            response = HttpResponse(
                result = HttpResponse.HttpResult.SUCCESS,
                path = HttpUrlPath("/foo_path/12345", "/template/<id>"),
                error = RuntimeException("my_error"),
                headers = mapOf("response_header" to "response_value"),
            ),
            durationMs = 60L,
            extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
        )

        assertThat(responseInfo.fields).isEqualTo(
            mapOf(
                "_host" to "foo.com",
                "_method" to "GET",
                "_path" to "/foo_path/12345",
                "_path_template" to "/template/<id>",
                "_query" to "my=query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "end",
                "_duration_ms" to "60",
                "_result" to "success",
                "_error_type" to "RuntimeException",
                "_error_message" to "my_error",
                "my_extra_key_1" to "my_extra_value_1",
                "my_extra_key_2" to "my_extra_value_2",
            ).toFields(),
        )
    }

    @Test
    fun testHTTPRequestExplicitPathTemplate() {
        val spanId = UUID.randomUUID()
        val requestInfo = HttpRequestInfo(
            host = "foo.com",
            method = "GET",
            path = HttpUrlPath("/my_path/12345", "/template/<id>"),
            query = "my=query",
            headers = mapOf("content-type" to "json"),
            spanId = spanId,
            extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
        )

        val responseInfo = HttpResponseInfo(
            request = requestInfo,
            response = HttpResponse(
                result = HttpResponse.HttpResult.SUCCESS,
                path = HttpUrlPath("/my_path/12345"),
                error = RuntimeException("my_error"),
                headers = mapOf("response_header" to "response_value"),
            ),
            durationMs = 60L,
            extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
        )

        assertThat(responseInfo.fields).isEqualTo(
            mapOf(
                "_host" to "foo.com",
                "_method" to "GET",
                "_path" to "/my_path/12345",
                "_path_template" to "/template/<id>",
                "_query" to "my=query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "end",
                "_duration_ms" to "60",
                "_result" to "success",
                "_error_type" to "RuntimeException",
                "_error_message" to "my_error",
                "my_extra_key_1" to "my_extra_value_1",
                "my_extra_key_2" to "my_extra_value_2",
            ).toFields(),
        )
    }

    @Test
    fun testHTTPResponseRequestAttributesOverride() {
        val spanId = UUID.randomUUID()

        val requestInfo = HttpRequestInfo(
            host = "api.bitdrift.io",
            method = "GET",
            path = HttpUrlPath("/my_path/12345"),
            query = "my=query",
            spanId = spanId,
            extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
        )

        assertThat(requestInfo.fields).isEqualTo(
            mapOf(
                "_host" to "api.bitdrift.io",
                "_method" to "GET",
                "_path" to "/my_path/12345",
                "_query" to "my=query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "start",
                "my_extra_key_1" to "my_extra_value_1",
            ).toFields(),
        )

        val responseInfo = HttpResponseInfo(
            request = requestInfo,
            response = HttpResponse(
                host = "foo.com",
                path = HttpUrlPath("/foo_path/12345"),
                query = "foo_query",
                result = HttpResponse.HttpResult.SUCCESS,
                error = RuntimeException("my_error"),
            ),
            durationMs = 60L,
            extraFields = mapOf("my_extra_key_2" to "my_extra_value_2"),
        )

        assertThat(responseInfo.fields).isEqualTo(
            mapOf(
                "_host" to "foo.com",
                "_method" to "GET",
                "_path" to "/foo_path/12345",
                "_query" to "foo_query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "end",
                "_duration_ms" to "60",
                "_result" to "success",
                "_error_type" to "RuntimeException",
                "_error_message" to "my_error",
                "my_extra_key_1" to "my_extra_value_1",
                "my_extra_key_2" to "my_extra_value_2",
            ).toFields(),
        )

        assertThat(responseInfo.matchingFields).isEqualTo(
            mapOf(
                "_request._host" to "api.bitdrift.io",
                "_request._method" to "GET",
                "_request._path" to "/my_path/12345",
                "_request._span_id" to spanId.toString(),
                "_request._span_name" to "_http",
                "_request._span_type" to "start",
                "_request._query" to "my=query",
                "_request.my_extra_key_1" to "my_extra_value_1",
            ).toFields(),
        )
    }
}
