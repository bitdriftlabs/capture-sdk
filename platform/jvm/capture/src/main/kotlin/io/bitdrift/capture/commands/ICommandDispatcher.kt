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
    fun dispatch(
        invocationId: Long,
        key: String,
        commandId: String?,
        sessionId: String,
        argumentNames: Array<String>,
        argumentValues: Array<String>,
    )
}
