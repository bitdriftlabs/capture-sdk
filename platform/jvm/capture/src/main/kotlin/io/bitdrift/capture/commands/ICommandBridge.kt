// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.providers.Field

internal interface ICommandBridge {
    fun registerCommand(
        loggerId: Long,
        key: String,
        dispatcher: ICommandDispatcher,
    )

    fun unregisterCommand(
        loggerId: Long,
        key: String,
    ): Boolean

    /**
     * @param errorCode a [CommandErrorCode.wire] value, or null when the command succeeded.
     * @param errorMessage the failure detail; null when the command succeeded.
     * @param fields the result context on success, the error context on failure.
     * @param attachmentFilename non-null exactly when [attachment] is.
     * @param attachmentContentType non-null exactly when [attachment] is.
     */
    fun completeCommand(
        invocationId: Long,
        errorCode: String?,
        errorMessage: String?,
        fields: Array<Field>,
        attachment: ByteArray?,
        attachmentFilename: String?,
        attachmentContentType: String?,
    )
}
