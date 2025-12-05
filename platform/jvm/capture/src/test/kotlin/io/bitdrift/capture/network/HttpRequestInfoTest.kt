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

class HttpRequestInfoTest {
    @Test
    fun testFields() {
        val spanId = UUID.randomUUID()
        val requestInfo =
            HttpRequestInfo(
                host = "api.bitdrift.io",
                method = "GET",
                path = HttpUrlPath("/my_path/12345", "/template/<id>"),
                query = "my=query",
                headers = mapOf("content-type" to "json"),
                spanId = spanId,
                extraFields = mapOf("my_extra_key_1" to "my_extra_value_1"),
                bytesExpectedToSendCount = 4,
            )

        assertThat(requestInfo.fields)
            .containsEntry("_host", requestInfo.fields["_host"])
            .hasEntrySatisfying("_host") { assertThat(it.toString()).isEqualTo("api.bitdrift.io") }
            .hasEntrySatisfying("_method") { assertThat(it.toString()).isEqualTo("GET") }
            .hasEntrySatisfying("_path") { assertThat(it.toString()).isEqualTo("/my_path/12345") }
            .hasEntrySatisfying("_path_template") { assertThat(it.toString()).isEqualTo("/template/<id>") }
            .hasEntrySatisfying("_query") { assertThat(it.toString()).isEqualTo("my=query") }
            .hasEntrySatisfying("_span_id") { assertThat(it.toString()).isEqualTo(spanId.toString()) }
            .hasEntrySatisfying("_span_name") { assertThat(it.toString()).isEqualTo("_http") }
            .hasEntrySatisfying("_span_type") { assertThat(it.toString()).isEqualTo("start") }
            .hasEntrySatisfying("_request_body_bytes_expected_to_send_count") { assertThat(it.toString()).isEqualTo("4") }
            .hasEntrySatisfying("my_extra_key_1") { assertThat(it.toString()).isEqualTo("my_extra_value_1") }

        assertThat(requestInfo.matchingFields)
            .hasEntrySatisfying("_headers.content-type") { assertThat(it.toString()).isEqualTo("json") }
    }
}
