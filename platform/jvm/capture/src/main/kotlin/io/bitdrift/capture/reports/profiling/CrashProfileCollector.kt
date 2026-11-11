// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.content.Context
import android.util.Log
import androidx.annotation.RequiresApi
import io.bitdrift.capture.Capture.LOG_TAG
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.CrashProfileImportFailure
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/**
 * Subscribes to Android system-triggered ANR (API 36+) and Java OutOfMemoryError (API 37+)
 * profiles after local startup opt-in, while fatal issue reporting and their runtime flags are
 * enabled. Automatic collection adopts result files; manual imports borrow them. Native attribution
 * only receives SDK-owned paths, never a borrowed platform result.
 *
 * In automatic mode the SDK exclusively owns each enabled trigger type: Android has no
 * per-owner trigger registration or result tag for system triggers, so a host registration of the
 * same type is indistinguishable. Existing preferences record claimed types because registrations
 * survive process restarts; opting out or disabling a flag removes only those claimed types on
 * the next listener start. Per-kind flags are sampled at start, not subscribed to: the generic
 * platform-events listener suppresses duplicate starts. Native attribution still checks live flags.
 * ANR source facts
 * survive the source process through its exit record's process-state summary, which the SDK
 * therefore also owns while ANR profiles are enabled.
 *
 * Rust starts this listener after loading runtime configuration, so flags are current.
 *
 * An unmatched live result remains in SDK-private storage, outside Android's profiling cleanup.
 * The next process resubmits it once, after its own startup report pass.
 *
 * @param encodeSource encodes this process's facts as an opaque `CrashProfileSource`.
 * @param submit hands a result path, its kind ([ANR] or [OOM]) and whether an unmatched result
 * should be kept for the next process to native attribution.
 * @param executor serializes reconciliation, storage, and platform callbacks.
 */
@RequiresApi(API_ANR)
internal class CrashProfileCollector(
    private val context: Context,
    private val profiler: ISystemTriggerProfiler,
    private val runtime: Runtime,
    private val encodeSource: (CrashProfileFacts) -> ByteArray?,
    private val submit: (path: String, kind: String, retainUnmatched: Boolean) -> Unit,
    private val executor: Executor,
    private val supportsOom: Boolean,
    private val configuration: Configuration,
    private val preferences: IPreferences,
    private val store: CrashProfileStore,
) : IEventListenerLogger {
    @Volatile
    private var kinds: Set<String> = emptySet()

    private val generation = AtomicLong()

    @Volatile
    private var stopped = false

    // Only accessed on [executor].
    private val resubmittedKinds = mutableSetOf<String>()
    private val pendingOwnershipClears = mutableSetOf<String>()

    override fun start() {
        stopped = false
        val started = generation.get()
        executor.execute {
            if (isCurrent(started)) {
                Log.d(LOG_TAG, "Starting crash profile collector: generation=$started")
                guarded("reconcile") { reconcile(started) }
            }
        }
    }

    override fun stop() {
        // Invalidate callbacks and imports immediately, including work already queued by Android.
        stopped = true
        generation.incrementAndGet()
        executor.execute {
            kinds = emptySet()
            CrashProfileSource.oomSource = null
            guarded("unregister") { profiler.unregister() }
            Log.d(LOG_TAG, "Stopped crash profile collector")
        }
    }

    fun importProfile(
        file: File,
        kind: CrashProfileKind,
        completion: (CaptureResult<Unit>) -> Unit,
    ) {
        val importedGeneration = generation.get()
        val wasStopped = stopped
        try {
            executor.execute {
                val result =
                    try {
                        check(!wasStopped && isCurrent(importedGeneration)) { "Crash profile collector was stopped" }
                        val enabled = enabledKinds()
                        check(kind.value in enabled) { "Crash profiling is disabled for ${kind.value}" }
                        checkNotNull(CrashProfileSource.current(context, encodeSource)) { "Crash profile source facts are unavailable" }
                        store.validateSource(file)
                        resubmitPending(enabled)
                        val imported = store.importProfile(file, kind)
                        // The persisted copy survives even if immediate native submission fails.
                        if (isCurrent(importedGeneration)) {
                            guarded("submit imported profile") { submit(imported.path, kind.value, true) }
                        } else {
                            Log.d(LOG_TAG, "Deferring persisted crash profile after collector stop")
                        }
                        CaptureResult.Success(Unit)
                    } catch (e: IOException) {
                        importFailure(e)
                    } catch (e: RuntimeException) {
                        importFailure(e)
                    }
                completion(result)
            }
        } catch (e: RejectedExecutionException) {
            completion(importFailure(e))
        }
    }

    private fun importFailure(error: Exception): CaptureResult.Failure {
        Log.w(LOG_TAG, "Crash profile import failed", error)
        return CaptureResult.Failure(CrashProfileImportFailure(error.message ?: "Crash profile import failed", error))
    }

    private fun enabledKinds(): Set<String> =
        buildSet {
            if (configuration.crashProfilingMode != null && configuration.enableFatalIssueReporting) {
                if (runtime.isEnabled(RuntimeFeature.CRASH_PROFILES_ANR)) add(ANR)
                if (supportsOom && runtime.isEnabled(RuntimeFeature.CRASH_PROFILES_OOM)) add(OOM)
            }
        }

    private fun resubmitPending(enabled: Set<String>) {
        val pendingKinds = enabled - resubmittedKinds
        val pending = store.pendingResults()
        Log.d(
            LOG_TAG,
            "Crash profile recovery: pendingCount=${pending.size}, enabled=$enabled, kindsToResubmit=$pendingKinds",
        )
        if (pendingKinds.isEmpty()) return
        pending
            .filter { (kind, _) -> kind in pendingKinds }
            .forEach { (kind, path) ->
                Log.d(LOG_TAG, "Resubmitting persisted crash profile: kind=$kind, path=$path, retainUnmatched=false")
                submit(path, kind, false)
            }
        resubmittedKinds += pendingKinds
    }

    private fun migrateLegacy(enabled: Set<String>) {
        if (enabled.isEmpty()) return
        // Migrate only SDK-retained legacy files, never unrelated platform results.
        profiler
            .pendingResults()
            .filter { (kind, _) -> kind in enabled }
            .forEach { (kind, path) ->
                guarded("migrate retained profile") { adopt(path, kind) }
            }
    }

    private fun adopt(
        path: String,
        kind: String,
    ): File {
        val source = File(path)
        Log.d(LOG_TAG, "Adopting system-triggered crash profile: kind=$kind, path=$path")
        try {
            return store.adopt(source, CrashProfileKind.entries.first { it.value == kind })
        } finally {
            // Automatic collection owns the original even when admission or persistence fails.
            if (source.isFile && !source.delete()) {
                Log.w(LOG_TAG, "Failed to remove owned crash profile: $path")
            }
        }
    }

    private fun reconcile(started: Long) {
        val enabled = enabledKinds()
        val source = if (enabled.isEmpty()) null else CrashProfileSource.current(context, encodeSource)
        val available = if (source == null) emptySet() else enabled
        Log.d(
            LOG_TAG,
            "Crash profile reconciliation: generation=$started, mode=${configuration.crashProfilingMode}, " +
                "fatalReporting=${configuration.enableFatalIssueReporting}, supportsOom=$supportsOom, " +
                "enabled=$enabled, sourceAvailable=${source != null}, available=$available",
        )
        kinds = available
        CrashProfileSource.oomSource = if (OOM in available) source else null
        if (source != null && ANR in available) {
            profiler.setProcessStateSummary(source)
        }
        val owned = if (configuration.crashProfilingMode == CrashProfilingMode.AUTOMATIC) available else emptySet()
        val previouslyOwned = setOf(ANR, OOM).filter { preferences.getString(ownershipKey(it)) != null }.toSet()
        val removed = previouslyOwned - owned - pendingOwnershipClears
        if (removed.isNotEmpty()) {
            // TODO: Establish a manual-mode handoff boundary. Capture startup success does not
            // await this cleanup, which can remove application triggers registered meanwhile.
            profiler.removeTriggers(removed)
            pendingOwnershipClears += removed
        }
        pendingOwnershipClears -= owned
        // Failed commits can erase the in-memory marker. Retry the disk clear independently,
        // without removing a trigger again after the application may have reclaimed it.
        pendingOwnershipClears.toList().forEach {
            check(preferences.setStringBlocking(ownershipKey(it), null)) {
                "Failed to clear crash profile trigger ownership: $it"
            }
            pendingOwnershipClears -= it
        }
        if (owned.isNotEmpty()) {
            // Retry every marker: a failed disk commit can still update in-memory preferences.
            owned.forEach {
                check(preferences.setStringBlocking(ownershipKey(it), it)) {
                    "Failed to persist crash profile trigger ownership: $it"
                }
            }
            profiler.addTriggers(owned)
        }
        if (owned.isNotEmpty() || removed.isNotEmpty()) {
            Log.d(LOG_TAG, "Crash profile triggers: enabled=$owned, removed=$removed")
        }
        guarded("migrate retained profiles") { migrateLegacy(available) }
        guarded("resubmit pending profiles") { resubmitPending(available) }
        if (owned.isEmpty()) {
            Log.d(LOG_TAG, "Crash profile listener not registered: no automatically owned triggers")
            profiler.unregister()
            return
        }
        Log.d(LOG_TAG, "Registering crash profile listener: generation=$started, owned=$owned")
        profiler.register { kind, path ->
            Log.d(LOG_TAG, "Crash profile callback received: generation=$started, kind=$kind, path=$path")
            // Direct requests, host-owned types and kinds not selected at start are left alone.
            if (!isCurrent(started)) {
                Log.d(
                    LOG_TAG,
                    "Ignoring stale crash profile callback: callbackGeneration=$started, " +
                        "currentGeneration=${generation.get()}, stopped=$stopped",
                )
                return@register
            }
            if (kind == null || kind !in kinds) {
                Log.d(LOG_TAG, "Ignoring crash profile callback: kind=$kind, enabled=$kinds")
                return@register
            }
            if (path == null) {
                Log.d(LOG_TAG, "System-triggered $kind profile failed")
            } else {
                guarded("adopt result") {
                    val adopted = adopt(path, kind)
                    if (isCurrent(started)) {
                        Log.d(LOG_TAG, "Submitting crash profile to native attribution: kind=$kind, path=${adopted.path}")
                        submit(adopted.path, kind, true)
                    } else {
                        Log.d(LOG_TAG, "Deferring adopted crash profile after collector stop: kind=$kind, path=${adopted.path}")
                    }
                }
            }
        }
        Log.d(LOG_TAG, "Crash profile listener registration completed: generation=$started")
    }

    private fun isCurrent(started: Long): Boolean = !stopped && generation.get() == started

    private inline fun guarded(
        operation: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Crash profile $operation failed", e)
        } catch (e: RuntimeException) {
            // Profiling service failures must never affect startup or crash reporting.
            Log.w(LOG_TAG, "Crash profile $operation failed", e)
        }
    }

    private fun ownershipKey(kind: String): String = "crash_profiles.owned.$kind"

    internal companion object {
        const val ANR = "anr"
        const val OOM = "oom"
    }
}
