// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

import io.bitdrift.capture.utils.JavaMapInteroptIssue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FieldProviderTest {
    @Test
    fun toFields_withJavaNullableValueMap_shouldNotCrash() {
        val mapWithNullValue: Map<String, String> =
            JavaMapInteroptIssue.buildMapWithNullableValue()

        val convertedFields = mapWithNullValue.toFields()

        assertThat(convertedFields.isEmpty()).isTrue()
    }

    @Test
    fun mapField_encodes_simpleStringMap() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val mapField = FieldValue.MapField(map)

        val encoded = mapField.encode()

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = buffer.int
        assertThat(entryCount).isEqualTo(2)
    }

    @Test
    fun mapField_encodes_mixedTypeMap() {
        val map =
            mapOf<String, Any>(
                "string" to "hello",
                "long" to 42L,
                "bool" to true,
                "double" to 3.14,
                "bytes" to byteArrayOf(1, 2, 3),
            )
        val mapField = FieldValue.MapField(map)

        val encoded = mapField.encode()

        assertThat(encoded).isNotEmpty()
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = buffer.int
        assertThat(entryCount).isEqualTo(5)
    }

    @Test
    fun mapField_encodes_nestedMap() {
        val innerMap =
            mapOf<String, Any>(
                "inner_key" to "inner_value",
            )
        val outerMap =
            mapOf<String, Any>(
                "outer_key" to "outer_value",
                "nested" to innerMap,
            )
        val mapField = FieldValue.MapField(outerMap)

        val encoded = mapField.encode()

        assertThat(encoded).isNotEmpty()
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = buffer.int
        assertThat(entryCount).isEqualTo(2)
    }

    @Test
    fun mapField_encodes_emptyMap() {
        val map = emptyMap<String, Any>()
        val mapField = FieldValue.MapField(map)

        val encoded = mapField.encode()

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = buffer.int
        assertThat(entryCount).isEqualTo(0)
    }

    @Test
    fun field_withMapField_hasCorrectValueType() {
        val map = mapOf<String, Any>("key" to "value")
        val field = Field("test_key", FieldValue.MapField(map))

        assertThat(field.valueType).isEqualTo(2)
    }

    @Test
    fun field_withMapField_byteArrayValueReturnsEncodedMap() {
        val map = mapOf<String, Any>("key" to "value")
        val field = Field("test_key", FieldValue.MapField(map))

        val encoded = field.byteArrayValue

        assertThat(encoded).isNotEmpty()
    }

    @Test
    fun typedFieldOf_createsSingleFieldArray() {
        val field = typedFieldOf("key", FieldValue.StringField("value"))

        assertThat(field).hasSize(1)
        assertThat(field[0].key).isEqualTo("key")
        assertThat(field[0].stringValue).isEqualTo("value")
    }

    @Test
    fun typedFieldOf_withMapField_createsSingleFieldArray() {
        val map = mapOf<String, Any>("nested_key" to "nested_value")
        val field = typedFieldOf("metadata", FieldValue.MapField(map))

        assertThat(field).hasSize(1)
        assertThat(field[0].key).isEqualTo("metadata")
        assertThat(field[0].valueType).isEqualTo(2)
    }

    @Test
    fun typedFieldsOf_createsMultipleFieldArray() {
        val fields =
            typedFieldsOf(
                "string_field" to FieldValue.StringField("value"),
                "binary_field" to FieldValue.BinaryField(byteArrayOf(1, 2, 3)),
                "map_field" to FieldValue.MapField(mapOf("key" to "value")),
            )

        assertThat(fields).hasSize(3)
        assertThat(fields[0].key).isEqualTo("string_field")
        assertThat(fields[0].valueType).isEqualTo(1)
        assertThat(fields[1].key).isEqualTo("binary_field")
        assertThat(fields[1].valueType).isEqualTo(0)
        assertThat(fields[2].key).isEqualTo("map_field")
        assertThat(fields[2].valueType).isEqualTo(2)
    }

    @Test
    fun typedFieldsOf_withEmptyInput_returnsEmptyArray() {
        val fields = typedFieldsOf()

        assertThat(fields).isEmpty()
    }
}
