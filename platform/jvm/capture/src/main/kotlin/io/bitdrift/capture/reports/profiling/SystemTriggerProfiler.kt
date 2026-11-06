// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.Executor
import java.util.function.Consumer

/** The platform calls used by [CrashProfileCollector], keyed by crash-profile kind. */
internal interface ISystemTriggerProfiler {
    fun addTriggers(kinds: Set<String>)

    /** Removes only trigger types that the caller previously claimed for the SDK. */
    fun removeTriggers(kinds: Set<String>)

    /** Stores source facts that Android returns with this process's exit record. */
    fun setProcessStateSummary(summary: ByteArray)

    /**
     * Registers for results once. Each result reports its kind (null for other trigger types) and
     * its file path (null when it failed).
     */
    fun register(onResult: (kind: String?, path: String?) -> Unit)

    fun unregister()

    /**
     * Legacy results retained in the platform directory, for migration to SDK-private storage.
     * Only files carrying the SDK's retained suffix qualify, never host results.
     */
    fun pendingResults(): List<Pair<String, String>>
}

/**
 * [ProfilingManager] system triggers. Callers run all methods on one executor, which also
 * receives results.
 */
@RequiresApi(API_ANR)
internal class SystemTriggerProfiler(
    private val context: Context,
    private val executor: Executor,
    supportsOom: Boolean,
) : ISystemTriggerProfiler {
    private val manager = context.getSystemService(ProfilingManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private var callback: Consumer<ProfilingResult>? = null

    // The OOM trigger constant is API 37; it is only read when the device supports it.
    @SuppressLint("InlinedApi")
    private val triggers: Map<String, Int> =
        buildMap {
            put(CrashProfileCollector.ANR, ProfilingTrigger.TRIGGER_TYPE_ANR)
            if (supportsOom) put(CrashProfileCollector.OOM, ProfilingTrigger.TRIGGER_TYPE_OOM)
        }

    override fun addTriggers(kinds: Set<String>) {
        val manager = checkNotNull(manager) { "ProfilingManager is unavailable" }
        val added = triggers.filterKeys { it in kinds }.values
        if (added.isNotEmpty()) {
            manager.addProfilingTriggers(added.map { ProfilingTrigger.Builder(it).build() })
        }
    }

    override fun removeTriggers(kinds: Set<String>) {
        val removed = triggers.filterKeys { it in kinds }.values
        if (removed.isNotEmpty()) {
            checkNotNull(manager) { "ProfilingManager is unavailable" }
                .removeProfilingTriggersByType(removed.toIntArray())
        }
    }

    override fun setProcessStateSummary(summary: ByteArray) {
        activityManager?.setProcessStateSummary(summary)
    }

    override fun register(onResult: (kind: String?, path: String?) -> Unit) {
        val manager = manager ?: return
        if (callback != null) {
            return
        }
        // Results produced after their source process died are delivered to this registration.
        val consumer =
            Consumer<ProfilingResult> { result ->
                val kind = triggers.entries.firstOrNull { it.value == result.triggerType }?.key
                val path = result.resultFilePath.takeIf { result.errorCode == ProfilingResult.ERROR_NONE }
                onResult(kind, path?.takeIf { it.isNotEmpty() })
            }
        manager.registerForAllProfilingResults(executor, consumer)
        callback = consumer
    }

    override fun unregister() {
        callback?.let { manager?.unregisterForAllProfilingResults(it) }
        callback = null
    }

    override fun pendingResults(): List<Pair<String, String>> =
        // Older SDK versions retained these in place. New profiles use CrashProfileStore instead.
        File(context.filesDir, RESULT_DIRECTORY)
            .listFiles()
            .orEmpty()
            .mapNotNull { file ->
                triggers.keys
                    .firstOrNull { kind -> file.name.endsWith(CrashProfileStore.RETAINED_SUFFIX + kind) }
                    ?.let { kind -> kind to file.path }
            }

    private companion object {
        const val RESULT_DIRECTORY = "profiling"
    }
}

/** The API level that introduced system-triggered ANR profiles. */
internal const val API_ANR = 36

/** The API level that introduced system-triggered Java OutOfMemoryError profiles. */
internal const val API_OOM = 37
