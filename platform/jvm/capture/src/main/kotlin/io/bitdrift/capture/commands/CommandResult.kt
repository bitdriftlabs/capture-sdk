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
 * A binary payload returned by a successful command, uploaded as an artifact. The payload is either
 * held in memory ([bytes]) or read from [file].
 *
 * @param bytes the payload; empty when the attachment is backed by [file].
 * @param file the file holding the payload, or null for an in-memory payload. The SDK takes
 * ownership of the file: it is read once, compressed into the SDK's own storage and deleted.
 * @param contentType the MIME type of the payload, e.g. `application/json`.
 */
class CommandAttachment private constructor(
    val bytes: ByteArray,
    val file: File?,
    val contentType: String,
) {
    /**
     * An in-memory payload.
     *
     * @param bytes the payload.
     * @param contentType the MIME type of the payload, e.g. `application/json`.
     */
    constructor(bytes: ByteArray, contentType: String) : this(bytes, null, contentType)

    /** Factories for attachments that are not held in memory. */
    companion object {
        /**
         * A payload already on disk. Large payloads should use this so they are never copied into
         * memory; the SDK reads [file] once and deletes it, so the caller must not touch it afterwards.
         *
         * @param file the file holding the payload.
         * @param contentType the MIME type of the payload, e.g. `application/octet-stream`.
         */
        @JvmStatic
        fun file(
            file: File,
            contentType: String,
        ): CommandAttachment = CommandAttachment(ByteArray(0), file, contentType)
    }
}
