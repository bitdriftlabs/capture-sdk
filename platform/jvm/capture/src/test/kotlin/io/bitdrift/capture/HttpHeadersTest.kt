// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.network.HTTPHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HttpHeadersTest {
    @Test
    fun test_authorization_headers_are_removed() {
        assertThat(
            HTTPHeaders.normalizeHeaders(
                mapOf(
                    "Authorization" to "foo",
                    "Proxy-Authorization" to "var",
                    "key" to "value",
                ),
            ),
        ).isEqualTo(
            mapOf("_headers.key" to "value"),
        )
    }
}
