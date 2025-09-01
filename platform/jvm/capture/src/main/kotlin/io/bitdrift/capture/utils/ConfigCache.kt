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

internal class MissingConfigKeyError(
    key: String,
) : Exception("Missing config key: $key")

internal class ConfigValueParsingError(
    key: String,
    expected: String,
    raw: String,
) : Exception("Config key '$key' expected $expected but got '$raw'")

internal object ConfigCache {
    private val cachedValues = mutableMapOf<File, Map<String, String>>()

    /** Returns a boolean value */
    fun getBooleanFlag(
        file: File,
        key: String,
    ): Boolean {
        val raw = readFlagValue(file, key)
        return when {
            raw.equals("true", ignoreCase = true) -> true
            raw.equals("false", ignoreCase = true) -> false
            else -> throw ConfigValueParsingError(key, "boolean", raw)
        }
    }

    /** Returns a double value. */
    fun getNumberFlag(
        file: File,
        key: String,
    ): Double =
        readFlagValue(file, key).toDoubleOrNull()
            ?: throw ConfigValueParsingError(key, "double", readFlagValue(file, key))

    /**
     * Returns the raw value as string from the configuration file
     */
    fun readFlagValue(
        file: File,
        flagValue: String,
    ): String {
        val values = cachedValues.getOrPut(file) { parse(file) }
        return values[flagValue] ?: throw MissingConfigKeyError(flagValue)
    }

    private fun parse(file: File): Map<String, String> {
        val values = mutableMapOf<String, String>()
        file.useLines { lines ->
            lines.forEachIndexed { idx, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachIndexed // skip empty lines
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
