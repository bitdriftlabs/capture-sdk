// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

import android.util.Log
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.IMetadataProvider

internal class MetadataProvider(
    private val dateProvider: DateProvider,
    private val ootbFieldProviders: List<FieldProvider>,
    private val customFieldProviders: List<FieldProvider>,
    private val errorHandler: ErrorHandler,
    private val errorLog: ((String, Throwable) -> Unit) = { message, throwable -> Log.w("capture", message, throwable) },
) : IMetadataProvider {
    override fun timestamp(): Long = dateProvider.invoke().time

    override fun ootbFields(): Array<Field> = fields(ootbFieldProviders)

    override fun customFields(): Array<Field> = fields(customFieldProviders)

    private fun fields(fieldProviders: List<FieldProvider>): Array<Field> {
        if (fieldProviders.isEmpty()) return emptyArray()

        val collectedMaps = arrayOfNulls<Map<String, String>>(fieldProviders.size)
        var totalSize = 0

        for (i in fieldProviders.indices) {
            try {
                val providedFields = fieldProviders[i]()
                collectedMaps[i] = providedFields
                totalSize += providedFields.size
            } catch (e: Throwable) {
                val message = "Field Provider \"${fieldProviders[i].javaClass.name}\" threw an exception"
                errorLog(message, e)
                errorHandler.handleError(message, e)
            }
        }

        if (totalSize == 0) return emptyArray()

        val result = arrayOfNulls<Field>(totalSize)
        var index = 0
        for (map in collectedMaps) {
            if (map != null) {
                for ((key, value) in map) {
                    result[index++] = Field(key, FieldValue.StringField(value))
                }
            }
        }
        return result.requireNoNulls()
    }
}
