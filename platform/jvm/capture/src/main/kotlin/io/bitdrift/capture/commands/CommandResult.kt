// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

/**
 * The outcome of a registered command.
 */
sealed class CommandResult {
    /**
     * The command completed successfully.
     *
     * @param attachment optional binary payload uploaded alongside the result.
     * @param context key-value pairs describing the result, captured as fields of the command log.
     */
    data class Success(
        val attachment: CommandAttachment? = null,
        val context: Map<String, String> = emptyMap(),
    ) : CommandResult()

    /**
     * The command failed.
     *
     * @param title a short identifier of the failure.
     * @param description an optional human readable description of the failure.
     * @param context key-value pairs describing the failure, captured as fields of the command log.
     */
    data class Error(
        val title: String,
        val description: String? = null,
        val context: Map<String, String> = emptyMap(),
    ) : CommandResult()
}

/**
 * A binary payload returned by a successful command, uploaded as an artifact. Carries the same
 * metadata as the iOS `CommandAttachment` so artifacts from both platforms look alike.
 *
 * @param bytes the payload.
 * @param filename the name the artifact is shown with, e.g. `memory.json`.
 * @param contentType the MIME type of the payload, e.g. `application/json`.
 */
class CommandAttachment(
    val bytes: ByteArray,
    val filename: String,
    val contentType: String,
)
