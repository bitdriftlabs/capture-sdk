// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.providers.toFields
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FieldProviderTest {
    @Test
    fun toFields_withJavaNullableValueMap_shouldNotCrash() {
        val mapWithNullValue: Map<String, String> =
            io.bitdrift.capture.utils.JavaMapInteroptIssue
                .buildMapWithNullableValue()

        val convertedFields = mapWithNullValue.toFields()

        assertThat(convertedFields).isEmpty()
    }

    @Test(expected = NullPointerException::class)
    fun nonSafeToFields_withJavaNullableValueMap_shouldExpectNpe() {
        val mapWithNullValue: Map<String, String> =
            io.bitdrift.capture.utils.JavaMapInteroptIssue
                .buildMapWithNullableValue()

        /**
         * This should throw NPE. See BIT-5914 for more context on original issue
         */
        mapWithNullValue.toFieldsNonSafe()
    }

    /**
     * Do not use in prod, only for test purposes
     */
    private fun Map<String, String>?.toFieldsNonSafe(): Map<String, FieldValue> =
        this?.entries?.associate {
            it.key to it.value.toFieldValue()
        } ?: emptyMap()
}
