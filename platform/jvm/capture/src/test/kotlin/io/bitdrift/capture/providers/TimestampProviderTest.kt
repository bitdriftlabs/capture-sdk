// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.Date

class TimestampProviderTest {
    @Test
    fun timestamp_uses_custom_date_provider() {
        val timestampProvider = TimestampProvider(DateProvider { Date(123L) })

        assertThat(timestampProvider.timestamp()).isEqualTo(123L)
    }
}
