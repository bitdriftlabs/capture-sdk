// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.file

import android.content.Context
import io.bitdrift.capture.commands.BuiltinCommandType
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope

internal class FileSizeCommand(
    context: Context,
) : FileCommand(context) {
    override val type = BuiltinCommandType.FILE_SIZE

    override suspend fun execute(scope: CommandScope): CommandResult {
        val path = subpath(scope) ?: return CommandResult.Error("file does not exist")
        val size =
            path
                .walkBottomUp()
                .sumOf { subpath -> if (subpath.isDirectory) 0 else subpath.length() }
        return CommandResult.Success(context = mapOf("file_size_kb" to (size / 1_024).toString()))
    }
}
