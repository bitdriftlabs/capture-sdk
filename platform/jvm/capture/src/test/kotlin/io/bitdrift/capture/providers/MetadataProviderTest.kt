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

class MetadataProviderTest {
    @Suppress("TooGenericExceptionThrown")
    @Test
    fun metadata_provider_processes_field_providers_in_order_and_swallows_exceptions() {
        // Processing of field getters continues even if one of them throws an exception.
        val throwingFieldGetter: FieldGetter =
            {
                throw RuntimeException("throw")
            }

        val workingFieldGetter: FieldGetter =
            {
                mapOf("key1" to "value3", "key2" to "value4")
            }
        val customFieldsProvider =
            CustomFieldsProvider(
                customFieldGetters = listOf(throwingFieldGetter, workingFieldGetter),
                errorHandler = mock { },
                errorLog = { _, _ -> },
            )

        assertThat(customFieldsProvider.customFields()).containsExactly(
            Field("key1", FieldValue.StringField("value3")),
            Field("key2", FieldValue.StringField("value4")),
        )
    }

    @Test
    fun custom_fields_provider_reuses_empty_custom_fields() {
        val customFieldsProvider =
            CustomFieldsProvider(
                customFieldGetters = emptyList(),
                errorHandler = mock(),
            )

        assertThat(customFieldsProvider.customFields()).isSameAs(customFieldsProvider.customFields()).isEmpty()
    }

    @Test
    fun timestamp_provider_uses_custom_date_provider() {
        val dateProvider = mock<DateProvider>()
        val date = Date()
        `when`(dateProvider.invoke()).thenReturn(date)

        assertThat(TimestampProvider(dateProvider).timestamp()).isEqualTo(date.time)
    }
}
