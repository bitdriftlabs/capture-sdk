// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import java.io.File

internal class CacheFormattingError(
    message: String,
) : Exception(message)

internal object ConfigCache {
    private val cachedValues = mutableMapOf<File, Map<String, String>>()

    fun readValues(file: File): Map<String, String> = cachedValues.getOrPut(file) { parse(file) }

    private fun parse(file: File): Map<String, String> {
        val values = mutableMapOf<String, String>()
        file.useLines { lines ->
            lines.forEachIndexed { idx, line ->
                val parts = line.split(",", limit = 2)
                if (parts.size != 2) {
                    throw CacheFormattingError("Malformed config at line ${idx + 1}: '$line'")
                }
                values[parts[0].trim()] = parts[1].trim()
            }
        }
        return values
    }
}
