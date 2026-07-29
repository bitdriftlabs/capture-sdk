package io.bitdrift.capture.profiling

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ProfilingResult
import android.util.Log
import androidx.core.os.StackSamplingRequestBuilder
import androidx.core.os.requestProfiling
import io.bitdrift.capture.Capture
import io.bitdrift.capture.providers.fieldOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.function.Consumer
import kotlin.time.Duration.Companion.milliseconds

/**
 * TODO: Abstract this for different perfetto trace types
 */
internal class PerfettoProfiler(
    private val context: Context,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start profiling only applicable on API 35 and up
     */
    fun start() {
        coroutineScope.launch {
            startStackSamplingProfiling()
        }
    }

    private suspend fun startStackSamplingProfiling() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return
        }

        Log.d(DEBUG_TAG, "starting profiling now")

        Capture.Logger.logInfo {
            "Started Callstack Sample profiling "
        }

        val listener =
            Consumer<ProfilingResult> { profilingResult ->

                if (profilingResult.errorCode == ProfilingResult.ERROR_NONE) {
                    Log.d("CallStackProfiler", "Success: ${profilingResult.resultFilePath}")

                    extractBinaryInfo(profilingResult.resultFilePath)?.let {
                        Capture.Logger.logInfo(
                            fields = fieldOf("_perfetto_trace", it),
                        ) {
                            "Perfetto trace available "
                        }
                    }
                } else {
                    Capture.Logger.logInfo(
                        mapOf(
                            "profilingResult.errorCode" to profilingResult.errorCode.toString(),
                        ),
                    ) {
                        "Callstack Sample profiling"
                    }
                }
            }

        val cancellationSignal = CancellationSignal()

        Log.d(DEBUG_TAG, "requestProfiling now")

        requestProfiling(
            context,
            StackSamplingRequestBuilder()
                .setBufferSizeKb(10000)
                .setDurationMs(10000)
                .setSamplingFrequencyHz(100)
                .setCancellationSignal(cancellationSignal)
                .build(),
            Dispatchers.IO.asExecutor(),
            listener,
        )

        delay(PROFILING_DURATION)
        cancellationSignal.cancel()

        Log.d(DEBUG_TAG, "cancellationSignal.cancel now")

        Capture.Logger.logInfo {
            "Stopped Callstack Sample profiling "
        }
    }

    private fun extractBinaryInfo(resultFilePath: String?): ByteArray? {
        try {
            resultFilePath?.let { path ->
                return File(path).takeIf(File::exists)?.readBytes()
            }
        } catch (error: Throwable) {
            Log.d(DEBUG_TAG, "error thrown " + error.message)
        }
        return null
    }

    private companion object {
        val PROFILING_DURATION = 5000.milliseconds
        const val DEBUG_TAG = "CallStackProfiler"
    }
}
