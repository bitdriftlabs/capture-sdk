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

    fun completeCommand(
        invocationId: Long,
        error: String?,
        fields: Array<Field>,
        attachment: ByteArray?,
        attachmentContentType: String?,
    )
}
