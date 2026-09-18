// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * Placeholder for the not-yet-defined SDK-wide attachment type. Deliberately not a `data class`
 * wrapping a `ByteArray` here, to avoid the reference-equality footgun that comes with putting an
 * array inside a data class's generated `equals`/`hashCode`.
 */
class Attachment(
    /** The raw attachment bytes. */
    val bytes: ByteArray,
)

/**
 * The outcome of a custom command invocation, reported back to the live debugger/workflow engine.
 */
sealed class CommandResult {
    /**
     * The command completed successfully.
     *
     * @param attachment an optional binary/log attachment produced by the command.
     * @param context optional structured data describing the outcome.
     */
    data class Success(
        val attachment: Attachment? = null,
        val context: Map<String, String> = emptyMap(),
    ) : CommandResult()

    /**
     * The command failed, was cancelled, or was invoked with an unknown key.
     *
     * @param title a short, human-readable summary of the failure.
     * @param description optional additional detail.
     * @param context optional structured data describing the failure.
     */
    data class Error(
        val title: String,
        val description: String? = null,
        val context: Map<String, String> = emptyMap(),
    ) : CommandResult()
}
