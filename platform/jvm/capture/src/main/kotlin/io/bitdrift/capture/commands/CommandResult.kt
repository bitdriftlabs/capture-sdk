// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import java.io.File

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
     * @param code why it failed; [CommandErrorCode.HandlerFailed] unless the arguments were unusable.
     */
    data class Error(
        val title: String,
        val description: String? = null,
        val context: Map<String, String> = emptyMap(),
        val code: CommandErrorCode = CommandErrorCode.HandlerFailed,
    ) : CommandResult()
}

/**
 * A binary payload returned by a successful command, uploaded as an artifact.
 *
 * @param bytes the payload; empty for file-backed attachments.
 * @param file the file source, or null for bytes. Returning this attachment transfers ownership
 * to the SDK, which deletes the file after consumption or failure. Do not modify or reuse its path.
 * @param contentType the MIME type of the payload, e.g. `application/json`.
 */
class CommandAttachment private constructor(
    val bytes: ByteArray,
    val contentType: String,
    val file: File?,
) {
    constructor(bytes: ByteArray, contentType: String) : this(bytes, contentType, null)

    /** Factories for payloads held outside memory. */
    companion object {
        /** Creates an attachment without reading the file. Ownership transfers when returned. */
        @JvmStatic
        fun file(
            file: File,
            contentType: String,
        ): CommandAttachment = CommandAttachment(ByteArray(0), contentType, file.absoluteFile)
    }
}
