// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

/**
 * Facilitates forwarding an unexpected exception to the error handler, providing visibility into
 * unexpected failures.
 */
interface ErrorHandler {
    /**
     * Handles a single error.
     * @param detail a string identifying the action that resulted in an error.
     * @param e the throwable associated with the error.
     */
    fun handleError(detail: String, e: Throwable?)
}
