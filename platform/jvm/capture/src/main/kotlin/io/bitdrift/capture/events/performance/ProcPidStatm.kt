// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.Process
import io.bitdrift.capture.events.performance.ProcPidStatmFile.Companion.RESIDENT_ID
import io.bitdrift.capture.events.performance.ProcPidStatmFile.Companion.SIZE_ID

/**
 * The index count for values in this file starts at 1.
 * According to Linux spec: https://man7.org/linux/man-pages/man5/proc.5.html
 */
internal data class ProcPidStatmFile(
    /**
     * #1
     * Total program size. VSZ (virtual set size) in pages.
     * Must be multiplied by pageSizeKb.
     */
    val size: Long,
    /**
     * #2
     * RSS (resident set size) in pages.
     * Must be multiplied by pageSizeKb.
     */
    val resident: Long,
) {
    val pageSizeKb get() = PAGE_SIZE_KB

    companion object {
        const val PAGE_SIZE_KB = 4

        const val SIZE_ID = 1
        const val RESIDENT_ID = 2
    }
}

internal class ProcPidStatmFileReader(
    private val reader: FileReader = FileReader(),
) {
    private val pid: Int = Process.myPid()

    private val path = "/proc/$pid/statm"

    fun makeSnapshot(): ProcPidStatmFile? {
        val statmFile: String = reader.readFile(path).getOrNull() ?: return null

        val params = statmFile.split(" ")

        // Linux starts count for values in this file at 1. To keep the consistency with Linux spec
        // ID constant values also start at 1. That's why we do '-1' step to get values from the
        // array which starts count at 0.
        return ProcPidStatmFile(
            size = params[SIZE_ID - 1].toLongOrNull() ?: 0,
            resident = params[RESIDENT_ID - 1].toLongOrNull() ?: 0,
        )
    }
}
