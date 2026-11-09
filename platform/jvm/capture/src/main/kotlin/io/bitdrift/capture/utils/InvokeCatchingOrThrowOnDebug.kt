// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import android.util.Log
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.ContextHolder

/**
 * Safely invokes a customer facing callback with the given [arg]. On failure:
 * - Always logs a warning to logcat.
 * - In debug builds, throws [DebugCustomerCallbackException] so the customer is aware during development.
 * - In release builds, the exception is swallowed to avoid crashing the app.
 */
internal fun <T> ((T) -> Unit)?.invokeCatchingOrThrowOnDebug(arg: T) {
    runCatching {
        this?.invoke(arg)
    }.onFailure { throwable ->
        DebugCustomerCallbackException.createIfDebug(throwable)?.let { throw it }
    }
}

/**
 * Exception thrown only in debug builds when a customer-provided callback throws an unhandled exception.
 * This exception cannot be instantiated in release builds due to the private constructor
 * and the [createIfDebug] factory method which gates on [BuildTypeChecker.isDebuggable].
 */
internal class DebugCustomerCallbackException private constructor(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause) {
    companion object {
        fun createIfDebug(throwable: Throwable): DebugCustomerCallbackException? =
            if (ContextHolder.isInitialized && BuildTypeChecker.isDebuggable(ContextHolder.APP_CONTEXT)) {
                val message = "Unhandled exception in customer callback"
                Log.w(LOG_TAG, message, throwable)
                DebugCustomerCallbackException(message, throwable)
            } else {
                null
            }
    }
}
