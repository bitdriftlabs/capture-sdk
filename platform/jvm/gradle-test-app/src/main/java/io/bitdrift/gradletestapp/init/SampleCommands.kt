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
import io.bitdrift.capture.commands.CommandAttachment
import io.bitdrift.capture.commands.CommandHandle
import io.bitdrift.capture.commands.CommandScope
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
                if (arguments["is_attachment"].toBoolean()) {
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
                success(context = arguments)
            },
            Capture.Logger.registerCommand(
                key = "slow",
                dispatcher = Dispatchers.IO,
                timeout = 3.seconds,
            ) {
                announce()
                val seconds = argument("seconds")
                val duration = seconds.toLongOrNull()?.seconds ?: return@registerCommand error("invalid_seconds", seconds)
                delay(duration)
                success(context = mapOf("slept" to duration.toString()))
            },
            Capture.Logger.registerCommand("fail") {
                announce()
                error("Unsupported", description = "this command always fails")
            },
            Capture.Logger.registerCommand(
                key = "system_trace",
                dispatcher = Dispatchers.IO,
                timeout = 30.seconds,
            ) {
                announce()
                val durationMs =
                    arguments["duration_ms"]?.let {
                        it.toLongOrNull() ?: return@registerCommand error("invalid_duration_ms", it)
                    } ?: DEFAULT_TRACE_DURATION_MS
                val context = appContext ?: return@registerCommand error("no_context")
                when (val result = StackSamplingProfiler.capture(context, durationMs.milliseconds)) {
                    is StackSamplingProfiler.Result.Trace ->
                        success(
                            attachment = CommandAttachment(result.bytes, contentType = "application/octet-stream"),
                            context =
                                mapOf(
                                    "duration_ms" to durationMs.toString(),
                                    "trace_bytes" to result.bytes.size.toString(),
                                ),
                        )
                    is StackSamplingProfiler.Result.Failure -> error("profiling_failed", result.reason)
                }
            },
        )

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

    private const val LOG_TAG = "SampleCommands"
    private const val DEFAULT_TRACE_DURATION_MS = 5_000L
}
