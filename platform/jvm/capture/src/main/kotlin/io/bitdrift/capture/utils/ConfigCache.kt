// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import java.io.File

internal class CacheFormattingError : Exception()

/**
 * Parses per-line, comma-delimited key/value pairs into configuration values
 */
internal object ConfigCache {
    /**
     * Read configuration values from a file
     *
     * @param file File to read
     *
     * @return The values or Exception which occurred during parsing
     */
    fun readValues(file: File): Result<Map<String, Any>> =
        try {
            readValues(file.readText())
        } catch (exc: Exception) {
            Result.failure(exc)
        }

    /**
     * Read configuration values from text
     *
     * @param text to parse
     *
     * @return The values or Exception which occurred during parsing
     */
    fun readValues(text: String): Result<Map<String, Any>> {
        val values = HashMap<String, Any>()
        for (line in text.split("\n")) {
            val pair = line.split(",", limit = 2)
            if (pair.size == 2) {
                values[pair[0]] =
                    when (pair[1]) {
                        "true" -> true
                        "false" -> false
                        else -> pair[1]
                    }
            } else {
                return Result.failure(CacheFormattingError())
            }
        }
        return Result.success(values)
    }
}
