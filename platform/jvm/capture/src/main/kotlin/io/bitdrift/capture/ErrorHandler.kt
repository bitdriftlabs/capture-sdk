// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.util.Log
import io.bitdrift.capture.common.ErrorHandler

/**
 * Facilitates forwarding an unexpected exception to the error handler, providing visibility into
 * unexpected failures.
 */
internal class ErrorHandler : ErrorHandler {
    init {
        // Make sure this is loaded before we call into CaptureJniLibrary. Generally this should
        // already have been loaded.
        CaptureJniLibrary.load()
    }

    /**
     * Handles a single error.
     * @param detail a string identifying the action that resulted in an error.
     * @param e the throwable associated with the error.
     */
    override fun handleError(
        detail: String,
        e: Throwable?,
    ) {
        try {
            // Delegate to the JNI function over using the error reporter directly. This is done so
            // that we can reuse the report limits implemented on the native side.
            CaptureJniLibrary.reportError(
                "'$detail' failed: $e",
                object : StackTraceProvider {
                    override fun invoke(): String? = e?.stackTraceToString()
                },
            )
        } catch (e: Throwable) {
            Log.w("capture", "failed to report error to bitdrift service: ${e.javaClass.name}: ${e.message}")
        }
    }
}
