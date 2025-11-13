// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.aiworkflow

/**
 * JNI bridge for AI workflow instrumentation native library.
 *
 * This class provides access to native functions in the ai_workflow_instrumentation library.
 * Make sure to call [load] before using any of the native functions.
 */
internal object AiWorkflowInstrumentationJni {
    /**
     * Loads the shared library. This is safe to call multiple times.
     * Should be called before using any native functions.
     */
    fun load() {
        System.loadLibrary("ai_workflow_instrumentation")
    }

    /**
     * Gets the length of a string from a byte array.
     *
     * @param byteArray the byte array containing the string data
     * @return the length of the string, or 0 if an error occurs
     */
    external fun getStringLength(byteArray: ByteArray): Int
}
