// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

import android.os.Handler
import android.os.Looper

/**
 * Helper class to run code on the main thread
 */
class MainThreadHandler {
    /**
     * Handler for the main thread
     */
    val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Schedule the given code to run on the main thread
     */
    fun run(run: () -> Unit) {
        mainHandler.post { run() }
    }

    /**
     * Wraps utilities for main thread operations
     */
    companion object {
        /**
         * Returns true when current call is happening on the main thread
         */
        fun isOnMainThread(): Boolean = Looper.getMainLooper() == Looper.myLooper()
    }
}
