// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network

import io.bitdrift.capture.utils.toStringMap
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

        assertThat(requestInfo.fields.toStringMap()).isEqualTo(
            mapOf(
                "_host" to "api.bitdrift.io",
                "_method" to "GET",
                "_path" to "/my_path/12345",
                "_path_template" to "/template/<id>",
                "_query" to "my=query",
                "_span_id" to spanId.toString(),
                "_span_name" to "_http",
                "_span_type" to "start",
                "_request_body_bytes_expected_to_send_count" to "4",
                "my_extra_key_1" to "my_extra_value_1",
            ),
        )

        assertThat(requestInfo.matchingFields.toStringMap()).isEqualTo(
            mapOf(
                "_headers.content-type" to "json",
            ),
        )
    }
}
