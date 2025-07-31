// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.util.Log

/**
 * Utility functions for verifying SDK behavior in test applications.
 */
object VerificationUtils {
    /**
     * Intended to confirm the SDK is evaluating the passed message lambda
     * into Capture.Logger.log calls only when the SDK is properly initialized
     *
     *
     * @param calledFrom a tag or source identifier used for logging.
     * @return a lambda that logs and returns a predefined verification message when invoked.
     */
    @JvmStatic
    fun getDeferredMessage(calledFrom: String): String {
        val message = "This message should only be created upon successful bitdrift init. Called from $calledFrom"
        Log.i("GradleTestApp lambda verification", message)
        return message
    }
}
