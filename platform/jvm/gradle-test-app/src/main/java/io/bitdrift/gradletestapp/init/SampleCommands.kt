// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.bitdrift.capture.Capture
import io.bitdrift.capture.commands.CommandArgument
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandErrorCode
import io.bitdrift.capture.commands.CommandHandle
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.commands.argument
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import io.bitdrift.gradletestapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Sample live session commands that can be registered and unregistered at runtime.
 */
object SampleCommands {
    private val flags = ConcurrentHashMap<String, Boolean>()
    private val handles = mutableListOf<CommandHandle>()
    private val _registeredKeys = MutableStateFlow<List<String>>(emptyList())

    val registeredKeys: StateFlow<List<String>> = _registeredKeys.asStateFlow()

    private var appContext: Context? = null

    @Synchronized
    fun register(context: Context) {
        if (handles.isNotEmpty()) return
        appContext = context.applicationContext
        handles += registerAll()
        _registeredKeys.value = handles.map { it.key }
    }

    @Synchronized
    fun unregister() {
        handles.forEach { it.unregister() }
        handles.clear()
        _registeredKeys.value = emptyList()
    }

    @OptIn(ExperimentalBitdriftApi::class)
    private fun registerAll(): List<CommandHandle> =
        listOf(
            Capture.Logger.registerCommand("flip_flag") {
                announce()
                val flag = argument("flag")
                val previous = isEnabled(flag)
                flip(flag)
                Capture.Logger.setFeatureFlagExposure(flag, !previous)
                success(
                    context =
                        mapOf(
                            "from" to previous.toString(),
                            "to" to (!previous).toString(),
                        ),
                )
            },
            Capture.Logger.registerCommand("memory_dump") {
                announce()
                val runtime = Runtime.getRuntime()
                val dump =
                    """{"max":${runtime.maxMemory()},"total":${runtime.totalMemory()},"free":${runtime.freeMemory()}}"""
                if (arguments["is_attachment"].isTruthy()) {
                    success(attachment = CommandAttachment(dump.toByteArray(), contentType = "application/json"))
                } else {
                    success(context = mapOf("memory" to dump))
                }
            },
            Capture.Logger.registerCommand("set_field") {
                announce()
                val name = argument("name")
                val value = argument("value")
                Capture.Logger.addField(name, value)
                success(context = mapOf(name to value))
            },
            Capture.Logger.registerCommand("echo") {
                announce()
                Capture.Logger.logInfo(mapOf("command_key" to key, "arguments" to arguments.toString())) {
                    "echo command executed"
                }
                success(context = arguments.mapValues { it.value.toString() })
            },
            Capture.Logger.registerCommand("slow") {
                announce()
                val seconds = argument("seconds")
                val duration =
                    seconds.toLongOrNull()?.seconds
                        ?: return@registerCommand error(
                            listOfNotNull("invalid_seconds", seconds.takeUnless { it.isBlank() }).joinToString(": "),
                            code = CommandErrorCode.InvalidArguments,
                        )
                delay(duration)
                success(context = mapOf("slept" to duration.toString()))
            },
            Capture.Logger.registerCommand("fail") {
                announce()
                error("Unsupported: this command always fails")
            },
            Capture.Logger.registerCommand("system_trace") {
                announce()
                val requestedType = if ("profiling_type" in arguments) argument<String>("profiling_type") else "STACK_SAMPLING"
                val profilingType =
                    AppProfiler.ProfilingType.entries.firstOrNull { it.name.equals(requestedType, ignoreCase = true) }
                        ?: return@registerCommand error(
                            listOfNotNull("invalid_profiling_type", requestedType.takeUnless { it.isBlank() }).joinToString(": "),
                            code = CommandErrorCode.InvalidArguments,
                        )
                val durationMs =
                    if (profilingType.usesDuration) {
                        (if ("duration_ms" in arguments) argument<Long>("duration_ms") else DEFAULT_TRACE_DURATION_MS).also {
                            if (it !in 1..60_000) return@registerCommand error("invalid_duration", code = CommandErrorCode.InvalidArguments)
                        }
                    } else {
                        null
                    }
                val request =
                    when (profilingType) {
                        // A heap dump has no duration; its buffer size is required instead.
                        AppProfiler.ProfilingType.JAVA_HEAP_DUMP ->
                            AppProfiler.Request.JavaHeapDump(argument<Long>("buffer_size_kb").toIntSaturated())
                        AppProfiler.ProfilingType.HEAP_PROFILE -> AppProfiler.Request.HeapProfile(checkNotNull(durationMs).toInt())
                        AppProfiler.ProfilingType.STACK_SAMPLING -> AppProfiler.Request.StackSampling(checkNotNull(durationMs).toInt())
                        AppProfiler.ProfilingType.SYSTEM_TRACE -> AppProfiler.Request.SystemTrace(checkNotNull(durationMs).toInt())
                    }
                val context = appContext ?: return@registerCommand error("no_context")
                val workload =
                    if (arguments["symbolication_workload"].isTruthy()) {
                        PerfettoWorkload.start(profilingType, durationMs ?: DEFAULT_TRACE_DURATION_MS)
                    } else {
                        null
                    }
                captureProfile(context, request, durationMs, workload)
            },
            Capture.Logger.registerCommand("heap_profile_demo") {
                announce()
                // Every argument is required; the platform clamps each value to its own bounds.
                val request =
                    AppProfiler.Request.HeapProfile(
                        durationMs = argument<Long>("duration_ms").toIntSaturated(),
                        bufferSizeKb = argument<Long>("buffer_size_kb").toIntSaturated(),
                        samplingIntervalBytes = argument<Long>("sampling_interval_bytes"),
                        trackJavaAllocations = argument<Boolean>("track_java_allocations"),
                    )
                val context = appContext ?: return@registerCommand error("no_context")
                captureProfile(context, request, request.durationMs.toLong())
            },
            Capture.Logger.registerCommand("java_heap_dump_demo") {
                announce()
                // The only argument is required; the platform clamps it to its own bounds.
                val request = AppProfiler.Request.JavaHeapDump(bufferSizeKb = argument<Long>("buffer_size_kb").toIntSaturated())
                val context = appContext ?: return@registerCommand error("no_context")
                captureProfile(context, request, durationMs = null)
            },
        ) + if (BuildConfig.E2E_TRACE_COPY) E2eCommands.register(checkNotNull(appContext)) else emptyList()

    private suspend fun CommandScope.captureProfile(
        context: Context,
        request: AppProfiler.Request,
        durationMs: Long?,
        workload: PerfettoWorkload? = null,
    ): CommandResult {
        var unclaimed: File? = null
        try {
            val result =
                try {
                    AppProfiler.capture(context, request).also { result ->
                        if (result is AppProfiler.Result.Saved) unclaimed = File(result.path)
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { workload?.close() }
                }
            val outcome =
                profileResult(request.type, durationMs, result, context.filesDir) { file ->
                    if (BuildConfig.E2E_TRACE_COPY) saveE2eTraceCopy(file)
                }
            // Preserve successful platform files for ProfileExporter; only the dedicated copy transfers.
            if (outcome is CommandResult.Success) unclaimed = null
            return outcome
        } finally {
            unclaimed?.deleteUnclaimed()
        }
    }

    /** Prepares an immutable SDK-owned copy outside the platform directory used by ProfileExporter. */
    internal suspend fun profileResult(
        profilingType: AppProfiler.ProfilingType,
        durationMs: Long?,
        result: AppProfiler.Result,
        filesDir: File,
        exportTrace: suspend (File) -> Unit = {},
    ): CommandResult =
        when (result) {
            is AppProfiler.Result.Saved -> {
                var unclaimed: File? = null
                try {
                    val outcome =
                        withContext(Dispatchers.IO) {
                            val source = File(result.path)
                            if (!source.isFile) throw IOException("trace file missing")
                            val size = source.length()
                            Log.i(LOG_TAG, "Profiling result type=${profilingType.name} bytes=$size")
                            val owned = File.createTempFile("command-profile-", ".perfetto-trace", filesDir)
                            unclaimed = owned
                            source.inputStream().use { input ->
                                owned.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        ensureActive()
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }
                            if (owned.length() != size) throw IOException("trace file changed while copying")
                            exportTrace(owned)
                            ensureActive()
                            Log.d(LOG_TAG, "Prepared profiling attachment type=${profilingType.name} bytes=$size path=${owned.path}")
                            CommandResult.Success(
                                attachment = CommandAttachment.file(owned, contentType = "application/vnd.bitdrift.perfetto-trace"),
                                context =
                                    buildMap {
                                        put("profiling_type", profilingType.name)
                                        durationMs?.takeIf { profilingType.usesDuration }?.let { put("duration_ms", it.toString()) }
                                        put("trace_bytes", size.toString())
                                    },
                            )
                        }
                    // withContext can lose its result to cancellation; retain cleanup until it returns.
                    unclaimed = null
                    outcome
                } catch (e: IOException) {
                    Log.w(LOG_TAG, "Could not prepare profiling attachment", e)
                    CommandResult.Error(
                        listOfNotNull("profiling_failed", e.message?.takeUnless { it.isBlank() }).joinToString(": "),
                    )
                } finally {
                    unclaimed?.deleteUnclaimed()
                }
            }
            is AppProfiler.Result.Failure -> {
                Log.w(
                    LOG_TAG,
                    "Profiling failed type=${profilingType.name} errorName=${result.errorName} " +
                        "errorCode=${result.errorCode} message=${result.message}",
                )
                CommandResult.Error(
                    listOfNotNull("profiling_failed", result.errorName, result.message.takeUnless { it.isBlank() }).joinToString(": "),
                    context =
                        buildMap {
                            put("profiling_type", profilingType.name)
                            result.errorCode?.let { put("error_code", it.toString()) }
                            result.errorName?.let { put("error_name", it) }
                        },
                )
            }
        }

    /**
     * Keeps an adb-pullable copy of an uploaded trace for the android-profiling-e2e skill, which
     * compares it byte for byte with what the backend serves. Only built with `-Pe2eTraceCopy=true`.
     */
    private suspend fun CommandScope.saveE2eTraceCopy(file: File) {
        val context = appContext ?: throw IOException("application context is unavailable")
        val dir = context.getExternalFilesDir("e2e-traces") ?: throw IOException("external storage is unavailable")
        val exporter =
            ProfileExporter(context.packageName, { File(context.filesDir, "profiling") }, { dir }, ProfileExporter.AdbAccess.EXPORT)
        when (val result = exporter.export(file)) {
            is ProfileExporter.Result.Exported -> {
                val out = File(dir, file.name)
                Log.i(
                    LOG_TAG,
                    "E2E trace copy path=${out.absolutePath} bytes=${file.length()} sessionId=$sessionId commandId=$commandId",
                )
            }
            is ProfileExporter.Result.Failed -> throw IOException("E2E trace copy failed: ${result.reason}")
        }
    }

    private fun File.deleteUnclaimed() {
        Log.d(LOG_TAG, "Deleting unclaimed profile $path")
        if (exists() && !delete()) Log.w(LOG_TAG, "Could not delete unclaimed profile $path")
    }

    /** Narrows to an Int without wrapping; the platform clamps the value to its own bounds. */
    private fun Long.toIntSaturated(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    private fun CommandScope.announce() {
        val message = "Command $key invoked with ${arguments.ifEmpty { "no arguments" }}"
        Log.i(LOG_TAG, "$message (commandId=$commandId, sessionId=$sessionId)")
        val context = appContext ?: return
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private suspend fun isEnabled(flag: String): Boolean =
        withContext(Dispatchers.IO) {
            delay(100.milliseconds)
            flags[flag] ?: false
        }

    private suspend fun flip(flag: String) {
        withContext(Dispatchers.IO) {
            delay(100.milliseconds)
            flags[flag] = !(flags[flag] ?: false)
        }
    }

    /** Accepts both a boolean argument and the text "true", since the live debugger may send either. */
    private fun CommandArgument?.isTruthy(): Boolean =
        when (this) {
            is CommandArgument.Bool -> value
            is CommandArgument.Text -> value.toBoolean()
            else -> false
        }

    private const val LOG_TAG = "SampleCommands"
    private const val DEFAULT_TRACE_DURATION_MS = 5_000L
}
