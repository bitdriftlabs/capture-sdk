// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class FileReader {
    fun readFile(path: String): Result<String> =
        try {
            val file = File(path)
            Result.success(file.readText())
        } catch (ex: Exception) {
            Result.failure(handleException(ex))
        }

    private fun FileNotFoundException.isPermissionDenied(): Boolean = message?.contains(PERMISSION_DENIED_PATTERN) ?: false

    private fun handleException(ex: Exception): Exception =
        if (ex is FileNotFoundException && ex.isPermissionDenied()) {
            IOException(ex)
        } else {
            ex
        }

    companion object {
        private const val PERMISSION_DENIED_PATTERN = "open failed: EACCES (Permission denied)"
    }
}
