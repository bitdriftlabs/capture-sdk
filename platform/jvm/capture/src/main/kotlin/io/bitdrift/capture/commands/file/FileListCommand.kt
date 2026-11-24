// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.file

import android.content.Context
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope

internal class FileListCommand(
    context: Context,
) : FileCommand(context) {
    override val type = BuiltinCommandType.FILE_LIST

    override suspend fun execute(scope: CommandScope): CommandResult {
        // number of bytes to allow in context before making an attachment
        val listContentsMaxBytes = 1_000
        val path = subpath(scope) ?: return CommandResult.Error("file does not exist")
        val list = path.listFiles() ?: return CommandResult.Error("failed to access path")
        list.sort()
        val contents = list.joinToString("\n") { subpath -> subpath.name }
        val context = mutableMapOf("file_count" to list.count().toString())
        val attachment =
            if (contents.length > listContentsMaxBytes) {
                CommandAttachment(
                    contents.encodeToByteArray(),
                    "text/plain",
                )
            } else {
                context["contents"] = contents
                null
            }
        return CommandResult.Success(attachment, context)
    }
}
