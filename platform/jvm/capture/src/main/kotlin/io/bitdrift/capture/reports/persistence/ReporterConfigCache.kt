// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import java.io.File
import java.io.IOException

internal object ReporterConfigCache {
    @JvmStatic
    fun readValues(file: File): Map<String, Any>? =
        try {
            readValues(file.readText())
        } catch (exc: IOException) {
            null
        }

    @JvmStatic
    fun readValues(text: String): Map<String, Any>? {
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
                // improperly formatted file
                return null
            }
        }
        return values
    }
}
