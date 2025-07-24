// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.utils.JavaMapInteroptIssue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FieldProviderTest {
    @Test
    fun toFields_withJavaNullableValueMap_shouldNotCrash() {
        val mapWithNullValue: Map<String, String> =
            JavaMapInteroptIssue.buildMapWithNullableValue()

        val convertedFields = mapWithNullValue.toFields()

        assertThat(convertedFields).isEmpty()
    }
}
