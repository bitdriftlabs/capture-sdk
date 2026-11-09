// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import androidx.annotation.VisibleForTesting
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
     *
     * @throws java.io.IOException, CacheFormattingError
     */
    fun readValues(file: File): Map<String, Any> = readValues(file.readText())

    /**
     * Read configuration values from text
     *
     * @param text to parse
     *
     * @return The values or Exception which occurred during parsing
     *
     * @throws CacheFormattingError if the file format does not match
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun readValues(text: String): Map<String, Any> =
        text
            .lines()
            .map { line ->
                val pair = line.split(",", limit = 2)
                if (pair.size == 2) {
                    Pair(
                        pair[0],
                        when (pair[1]) {
                            "true" -> true
                            "false" -> false
                            else -> pair[1]
                        },
                    )
                } else {
                    throw CacheFormattingError()
                }
            }.associate { it.first to it.second }
}
