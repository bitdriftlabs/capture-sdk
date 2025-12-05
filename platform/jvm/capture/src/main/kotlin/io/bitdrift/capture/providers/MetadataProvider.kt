// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.providers

import android.util.Log
import io.bitdrift.capture.EMPTY_INTERNAL_FIELDS
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.IMetadataProvider
import io.bitdrift.capture.InternalFields

internal class MetadataProvider(
    private val dateProvider: DateProvider,
    private val ootbFieldProviders: List<FieldProvider>,
    private val customFieldProviders: List<FieldProvider>,
    private val errorHandler: ErrorHandler,
    private val errorLog: ((String, Throwable) -> Unit) = { message, throwable -> Log.w("capture", message, throwable) },
) : IMetadataProvider {
    override fun timestamp(): Long = dateProvider.invoke().time

    override fun ootbFields(): InternalFields = fields(ootbFieldProviders)

    override fun customFields(): InternalFields = fields(customFieldProviders)

    private fun fields(fieldProviders: List<FieldProvider>): InternalFields {
        if (fieldProviders.isEmpty()) return EMPTY_INTERNAL_FIELDS

        // Pre-collect all fields to avoid intermediate List allocations from flatMap
        val result = ArrayList<Field>()
        for (fieldProvider in fieldProviders) {
            try {
                val providedFields = fieldProvider()
                for ((key, value) in providedFields) {
                    result.add(Field(key = key, value = value.toFieldValue()))
                }
            } catch (e: Throwable) {
                // We cannot log to our logger as we are in the middle of processing
                // a log and want to avoid an infinite cycle of logs.
                // The issue is not with our code but customer's provider.
                val message = "Field Provider \"${fieldProvider.javaClass.name}\" threw an exception"
                errorLog(message, e)
                errorHandler.handleError(message, e)
            }
        }
        return result.toTypedArray()
    }
}
