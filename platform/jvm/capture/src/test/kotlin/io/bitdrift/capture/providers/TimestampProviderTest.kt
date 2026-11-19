// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import java.util.Date

class TimestampProviderTest {
    @Test
    fun timestamp_provider_uses_custom_date_provider() {
        val dateProvider = mock<DateProvider>()
        val date = Date()
        `when`(dateProvider.invoke()).thenReturn(date)

        assertThat(TimestampProvider(dateProvider).timestamp()).isEqualTo(date.time)
    }
}
