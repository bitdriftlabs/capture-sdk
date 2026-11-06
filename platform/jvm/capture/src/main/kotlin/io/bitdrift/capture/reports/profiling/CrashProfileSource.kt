// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting

/**
 * Typed facts about this process, captured once at SDK startup. Native code encodes them, with a
 * fresh run identity, into an opaque `CrashProfileSource` message.
 */
internal data class CrashProfileFacts(
    val pid: Int,
    val bootCount: Int,
    val processStartElapsedNs: Long,
    val wallNs: Long,
    val elapsedNs: Long,
)

/**
 * The encoded source-process facts that let a later system-triggered profile be attributed to an
 * SDK report. Kotlin never parses them: native code encodes them, the ANR exit record and JVM OOM
 * reports carry them, and native attribution decodes them. A delayed profile is always attributed
 * using the facts of the process that produced the report, never the process receiving it.
 */
internal object CrashProfileSource {
    private const val NANOS_PER_MILLI = 1_000_000L

    @Volatile
    private var current: ByteArray? = null

    /**
     * Source for an unhandled Java OutOfMemoryError in this process, or null while OOM profiles are
     * disabled. Read on the crashing thread, so it is prepared ahead of time.
     */
    @Volatile
    var oomSource: ByteArray? = null

    /** This process's encoded facts, or null when Android does not expose the boot count. */
    @RequiresApi(API_ANR)
    fun current(
        context: Context,
        encode: (CrashProfileFacts) -> ByteArray?,
    ): ByteArray? =
        current ?: synchronized(this) {
            current ?: capture(context)?.let(encode)?.also { current = it }
        }

    /** Forgets this process's facts so each test observes a fresh capture. */
    @VisibleForTesting
    fun resetForTest() {
        current = null
        oomSource = null
    }

    @RequiresApi(API_ANR)
    private fun capture(context: Context): CrashProfileFacts? {
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (bootCount < 0) {
            return null
        }
        // A paired REALTIME/BOOTTIME sample: keep the two reads adjacent.
        val wallNs = System.currentTimeMillis() * NANOS_PER_MILLI
        val elapsedNs = SystemClock.elapsedRealtimeNanos()
        return CrashProfileFacts(
            pid = Process.myPid(),
            bootCount = bootCount,
            processStartElapsedNs = Process.getStartElapsedRealtime() * NANOS_PER_MILLI,
            wallNs = wallNs,
            elapsedNs = elapsedNs,
        )
    }
}
