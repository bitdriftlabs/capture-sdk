// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.gradletestapp.diagnostics.profiling

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import io.bitdrift.gradletestapp.BuildConfig
import io.bitdrift.gradletestapp.diagnostics.fatalissues.FatalIssueGenerator
import io.bitdrift.gradletestapp.init.AppProfiler
import io.bitdrift.gradletestapp.init.CaptureSdkInitializer
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_API_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.BITDRIFT_URL_KEY
import io.bitdrift.gradletestapp.ui.fragments.ConfigurationSettingsFragment.Companion.SESSION_REPLAY_ENABLED_PREFS_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.function.Consumer

/** Opt-in sample-only evidence collection. Nothing is uploaded or assigned a parent report. */
@SuppressLint("LogNotTimber")
object TriggerProfilingExperiment {
    private const val TAG = "TriggerProfiling"
    private const val PREFS = "trigger-profiling"
    private val runId = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "trigger-profiling-evidence") }
    private var listener: Consumer<ProfilingResult>? = null

    fun onLaunch(context: Context) {
        if (!BuildConfig.E2E_TRIGGER_PROFILING || Build.VERSION.SDK_INT < 36) return
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.contains("installation_id")) {
            check(preferences.edit().putString("installation_id", UUID.randomUUID().toString()).commit())
        }
        executor.execute {
            record(context, "launch")
            recordExits(context)
            if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)) {
                listen(context)
            }
        }
    }

    fun runScenario(
        context: Context,
        scenario: String,
    ) {
        check(BuildConfig.E2E_TRIGGER_PROFILING) { "Trigger experiments are disabled" }
        if (Build.VERSION.SDK_INT < 36) {
            Log.e(TAG, "System-trigger profiling requires API 36; device=${Build.VERSION.SDK_INT}")
            return
        }
        executor.execute {
            try {
                when (scenario) {
                    "enable" -> {
                        listen(context)
                        manager(context).addProfilingTriggers(
                            triggerTypes().map { ProfilingTrigger.Builder(it).setRateLimitingPeriodHours(0).build() },
                        )
                        check(
                            context
                                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("enabled", true)
                                .commit(),
                        )
                        record(context, "enabled", JSONObject().put("trigger_types", JSONArray(triggerTypes())))
                    }
                    "disable" -> {
                        check(
                            context
                                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("enabled", false)
                                .commit(),
                        )
                        manager(context).removeProfilingTriggersByType(intArrayOf(ProfilingTrigger.TRIGGER_TYPE_ANR))
                        if (Build.VERSION.SDK_INT >= 37) {
                            manager(context).removeProfilingTriggersByType(intArrayOf(ProfilingTrigger.TRIGGER_TYPE_OOM))
                        }
                        unlisten(context)
                        record(context, "disabled")
                    }
                    "listen" -> listen(context)
                    "unlisten" -> unlisten(context)
                    "status" -> recordExits(context)
                    "direct" -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = AppProfiler.capture(context, AppProfiler.Request.StackSampling(durationMs = 5000))
                            executor.execute {
                                when (result) {
                                    is AppProfiler.Result.Saved -> record(context, "direct_saved", JSONObject().put("path", result.path))
                                    is AppProfiler.Result.Failure -> failure(context, "direct", IOException(result.message))
                                }
                            }
                        }
                    }
                    "offline_sdk" -> {
                        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                        check(
                            preferences
                                .edit()
                                .putString(BITDRIFT_API_KEY, "m0-local-only-key")
                                .putString(BITDRIFT_URL_KEY, "http://10.0.2.2:1")
                                .putBoolean(SESSION_REPLAY_ENABLED_PREFS_KEY, false)
                                .commit(),
                        )
                        check(CaptureSdkInitializer.initFromPreferences(context, preferences)) { "SDK initialization rejected" }
                        record(context, "offline_sdk_requested")
                    }
                    "recovered_anr", "fatal_anr", "oom" -> {
                        check(scenario != "oom" || Build.VERSION.SDK_INT >= 37) { "OOM trigger requires API 37" }
                        record(context, "scenario", JSONObject().put("scenario", scenario))
                        Handler(Looper.getMainLooper()).postDelayed({
                            when (scenario) {
                                "recovered_anr" -> FatalIssueGenerator.forceThreadSleepAnr()
                                "fatal_anr" -> FatalIssueGenerator.forceBlockingGetAnr()
                                "oom" -> FatalIssueGenerator.forceJvmOutOfMemoryCrash()
                            }
                        }, 2500)
                    }
                    else -> error("Unknown trigger scenario: $scenario")
                }
            } catch (e: SecurityException) {
                failure(context, scenario, e)
            } catch (e: IllegalArgumentException) {
                failure(context, scenario, e)
            } catch (e: UnsupportedOperationException) {
                failure(context, scenario, e)
            }
        }
    }

    @RequiresApi(36)
    private fun manager(context: Context): ProfilingManager =
        checkNotNull(context.getSystemService(ProfilingManager::class.java)) { "No ProfilingManager service" }

    @RequiresApi(36)
    private fun triggerTypes(): List<Int> =
        buildList {
            add(ProfilingTrigger.TRIGGER_TYPE_ANR)
            if (Build.VERSION.SDK_INT >= 37) add(ProfilingTrigger.TRIGGER_TYPE_OOM)
        }

    @RequiresApi(36)
    private fun listen(context: Context) {
        if (listener != null) {
            record(context, "already_listening")
            return
        }
        val callback = Consumer<ProfilingResult> { result -> saveResult(context, result) }
        manager(context).registerForAllProfilingResults(executor, callback)
        listener = callback
        record(context, "listening")
    }

    @RequiresApi(36)
    private fun unlisten(context: Context) {
        listener?.let { manager(context).unregisterForAllProfilingResults(it) }
        listener = null
        record(context, "not_listening")
    }

    @RequiresApi(36)
    private fun saveResult(
        context: Context,
        result: ProfilingResult,
    ) {
        val details =
            JSONObject()
                .put("trigger_type", result.triggerType)
                .put("error_code", result.errorCode)
                .put("error_message", result.errorMessage ?: JSONObject.NULL)
                .put("tag", result.tag ?: JSONObject.NULL)
        if (result.triggerType !in triggerTypes()) {
            record(context, "ignored_result", details)
            return
        }
        record(context, "callback", details)
        if (result.errorCode != ProfilingResult.ERROR_NONE) return
        val path = result.resultFilePath
        if (path == null) {
            failure(context, "callback", IOException("Successful result has no file"))
            return
        }
        details.put("original_filename", File(path).name)
        val target = File(evidenceDir(context), "${UUID.randomUUID()}.perfetto-trace")
        val partial = File("${target.path}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val started = SystemClock.elapsedRealtimeNanos()
            File(path).inputStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!partial.renameTo(target)) throw IOException("Could not publish copied profile")
            details
                .put("file", target.name)
                .put("raw_bytes", target.length())
                .put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
                .put("staging_elapsed_ns", SystemClock.elapsedRealtimeNanos() - started)
            record(context, "profile_saved", details)
        } catch (e: IOException) {
            failure(context, "copy", e)
        } catch (e: SecurityException) {
            failure(context, "copy", e)
        } finally {
            if (partial.exists() && !partial.delete()) Log.e(TAG, "Could not remove partial evidence file")
        }
    }

    @RequiresApi(36)
    private fun recordExits(context: Context) {
        val exits = context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 32)
        for (exit in exits) {
            val traceHeader =
                try {
                    if (exit.reason == android.app.ApplicationExitInfo.REASON_ANR) {
                        exit.traceInputStream?.use { it.readNBytes(2048).toString(Charsets.UTF_8) }
                    } else {
                        null
                    }
                } catch (e: IOException) {
                    failure(context, "exit_trace", e)
                    null
                } catch (e: SecurityException) {
                    failure(context, "exit_trace", e)
                    null
                }
            record(
                context,
                "exit_info",
                JSONObject()
                    .put("source_pid", exit.pid)
                    .put("source_process", exit.processName)
                    .put("timestamp_ms", exit.timestamp)
                    .put("reason", exit.reason)
                    .put("status", exit.status)
                    .put("trace_header", traceHeader ?: JSONObject.NULL)
                    .put("description", exit.description),
            )
        }
    }

    private fun evidenceDir(context: Context): File {
        val dir = checkNotNull(context.getExternalFilesDir("trigger-profiling")) { "No external evidence directory" }
        check(dir.isDirectory || dir.mkdirs()) { "Could not create evidence directory" }
        return dir
    }

    @RequiresApi(36)
    private fun failure(
        context: Context,
        operation: String,
        e: Exception,
    ) {
        Log.e(TAG, "Experiment failed: $operation", e)
        record(context, "failure", JSONObject().put("operation", operation).put("error", e.toString()))
    }

    @RequiresApi(36)
    private fun record(
        context: Context,
        event: String,
        details: JSONObject = JSONObject(),
    ) {
        val row =
            JSONObject()
                .put("installation_id", context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("installation_id", null))
                .put("event", event)
                // These describe the observer, never the source of a delayed profile.
                .put(
                    "observer",
                    JSONObject()
                        .put("run_id", runId)
                        .put("pid", Process.myPid())
                        .put("start_elapsed_ms", Process.getStartElapsedRealtime())
                        .put("wall_ms", System.currentTimeMillis())
                        .put("elapsed_ns", SystemClock.elapsedRealtimeNanos())
                        .put("uptime_ms", SystemClock.uptimeMillis())
                        .put("boot_count", Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
                        .put("api", Build.VERSION.SDK_INT)
                        .put("version_code", BuildConfig.VERSION_CODE)
                        .put("default_handler", Thread.getDefaultUncaughtExceptionHandler()?.javaClass?.name),
                ).put("details", details)
        FileOutputStream(File(evidenceDir(context), "events.jsonl"), true).use { output ->
            output.write("$row\n".toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Log.i(TAG, row.toString())
    }
}
