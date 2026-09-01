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
    private val customFieldGetters: List<FieldGetter>,
    private val errorHandler: ErrorHandler,
    private val errorLog: ((String, Throwable) -> Unit) = { message, throwable -> Log.w("capture", message, throwable) },
) : IMetadataProvider {
    override fun timestamp(): Long = dateProvider.invoke().time

    override fun customFields(): Array<Field> =
        if (customFieldGetters.isEmpty()) {
            EMPTY_FIELDS
        } else {
            fields(customFieldGetters)
        }

    private fun fields(fieldGetters: List<FieldGetter>): Array<Field> {
        val result = mutableListOf<Field>()
        for (fieldGetter in fieldGetters) {
            try {
                val providedFields = fieldGetter()
                for ((key, value) in providedFields) {
                    result.add(Field(key, FieldValue.StringField(value)))
                }
            } catch (e: Throwable) {
                // We cannot log to our logger as we are in the middle of processing
                // a log and want to avoid an infinite cycle of logs.
                // The issue is not with our code but customer's provider.
                val message = "Field Provider \"${fieldGetter.javaClass.name}\" threw an exception"
                errorLog(message, e)
                errorHandler.handleError(message, e)
            }
        }
        if (result.isEmpty()) return EMPTY_FIELDS
        return result.toTypedArray()
    }

    private companion object {
        val EMPTY_FIELDS = emptyArray<Field>()
    }
}
