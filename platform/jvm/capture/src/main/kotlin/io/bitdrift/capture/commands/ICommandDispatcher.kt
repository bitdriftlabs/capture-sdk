// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * Receives command invocations from the Rust logger. Every invocation must be completed exactly
 * once through `CaptureJniLibrary.completeCommand`.
 */
internal interface ICommandDispatcher {
    /**
     * @param argumentNames one entry per argument.
     * @param argumentTypes a `CommandArgument.TYPE_*` code per argument, parallel to [argumentNames].
     * @param argumentValues the boxed value per argument, parallel to [argumentNames]: a `String`,
     * `ByteArray`, `Long` (for both integer types), `Double` or `Boolean`.
     */
    fun dispatch(
        invocationId: Long,
        key: String,
        commandId: String?,
        sessionId: String,
        argumentNames: Array<String>,
        argumentTypes: IntArray,
        argumentValues: Array<Any?>,
    )
}
