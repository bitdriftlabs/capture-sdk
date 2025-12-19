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
        val dateProvider = mock<DateProvider>()
        `when`(dateProvider.invoke()).thenReturn(Date())

        // Processing of field providers continues even if one of them throws
        // an exception.
        val throwingFieldProvider1 =
            FieldProvider {
                throw RuntimeException("throw1")
            }
        val throwingFieldProvider2 =
            FieldProvider {
                throw RuntimeException("throw2")
            }

        val workingFieldProviders1 =
            FieldProvider {
                mapOf("key1" to "value1", "key2" to "value2")
            }
        val workingFieldProviders2 =
            FieldProvider {
                mapOf("key1" to "value3", "key2" to "value4")
            }

        val metadataProvider =
            MetadataProvider(
                dateProvider = dateProvider,
                ootbFieldProviders = listOf(throwingFieldProvider1, workingFieldProviders1),
                customFieldProviders = listOf(throwingFieldProvider2, workingFieldProviders2),
                errorHandler = mock { },
                errorLog = { _, _ -> },
            )

        assertThat(metadataProvider.ootbFields()).containsExactly(
            Field("key1", FieldValue.StringField("value1")),
            Field("key2", FieldValue.StringField("value2")),
        )
        assertThat(metadataProvider.customFields()).containsExactly(
            Field("key1", FieldValue.StringField("value3")),
            Field("key2", FieldValue.StringField("value4")),
        )
    }
}
