package io.bitdrift.capture.profiling

import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Consumer

internal class ProfilingService(private val appContext: Context) {

    fun install() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Log.w("miguel-profiling", "ProfilingManager is not available on this Android SDK version=${Build.VERSION.SDK_INT}")
            return
        }
        val profilingManager = appContext.getSystemService(ProfilingManager::class.java)
        val triggers: MutableList<ProfilingTrigger?> = ArrayList()
        val triggerBuilder = ProfilingTrigger.Builder(
            ProfilingTrigger.TRIGGER_TYPE_ANR
        )
        triggers.add(triggerBuilder.build())
        val singleThreadExecutor: Executor = Executors.newSingleThreadExecutor()
        val resultCallback = Consumer<ProfilingResult> { profilingResult ->
            if (profilingResult.errorCode == ProfilingResult.ERROR_NONE) {
                Log.d(
                    "miguel-profiling",
                    "Received profiling result file=" + profilingResult.resultFilePath
                )
            // setupProfileUploadWorker(profilingResult.resultFilePath)
            } else {
                Log.e(
                    "miguel-profiling",
                    "Profiling failed errorcode=" + profilingResult.errorCode + " errormsg=" + profilingResult.errorMessage
                )
            }
        }
        profilingManager.registerForAllProfilingResults(singleThreadExecutor, resultCallback)
        profilingManager.addProfilingTriggers(triggers)
        Log.i("miguel-profiling", "ProfilingService installed successfully")
    }
}