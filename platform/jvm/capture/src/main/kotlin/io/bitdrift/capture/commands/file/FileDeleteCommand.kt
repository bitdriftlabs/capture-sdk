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
import io.bitdrift.capture.commands.typedArgument

internal class FileDeleteCommand(
    context: Context,
) : FileCommand(context) {
    override val type = BuiltinCommandType.FILE_DELETE

    override suspend fun execute(scope: CommandScope): CommandResult {
        val path =
            subpath(scope, pathRequired = true) ?: return CommandResult.Error("file does not exist")
        if (path.isFile && path.delete()) {
            return CommandResult.Success()
        } else if (path.isDirectory) {
            if (!scope.arguments.contains("recursive")) {
                return CommandResult.Error(
                    "did not delete file",
                    "declining to delete directory without recursive set to true",
                )
            }
            if (scope.typedArgument("recursive", Boolean::class) && path.deleteRecursively()) {
                return CommandResult.Success()
            }
        }
        return CommandResult.Error("failed to delete file")
    }
}
