// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.file

import android.content.Context
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.IBuiltinCommand
import io.bitdrift.capture.commands.MissingCommandArgumentException
import java.io.File
import kotlin.collections.contains
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.relativeToOrNull

internal abstract class FileCommand(
    private val context: Context,
) : IBuiltinCommand {
    override val timeoutMs: Long = 10_000
    override val artifactType: String = "file"

    internal fun subpath(
        scope: CommandScope,
        pathRequired: Boolean = false,
    ): File? {
        val field = "path"
        val cache = context.cacheDir
        if (!scope.arguments.contains(field)) {
            if (pathRequired) {
                throw MissingCommandArgumentException(field)
            }
            return cache
        }

        val path = Path(cache.absolutePath, scope.argument(field))
        if (path.relativeToOrNull(Path(cache.absolutePath)) != null) {
            val file = File(path.absolute().toString())
            try {
                if (file.exists()) {
                    return file
                }
            } catch (_: SecurityException) {
                //
            }
        }
        return null
    }
}
