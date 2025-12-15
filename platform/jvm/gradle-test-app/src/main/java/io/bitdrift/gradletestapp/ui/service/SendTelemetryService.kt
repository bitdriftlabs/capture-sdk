package io.bitdrift.gradletestapp.ui.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.bitdrift.capture.Capture

@SuppressLint("LogNotTimber")
class SendTelemetryService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        Log.i("miguel-bd_", "SendTelemetryService.onBind() called on process ${android.os.Process.myPid()}, thread=${Thread.currentThread().name}")
        Capture.Logger.logInfo { "SendTelemetryService.onBind() called on process ${android.os.Process.myPid()}, thread=${Thread.currentThread().name}" }
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("miguel-bd_", "SendTelemetryService.onStartCommand() called on process ${android.os.Process.myPid()}, thread=${Thread.currentThread().name}")
        Capture.Logger.logInfo { "SendTelemetryService.onStartCommand() called on process ${android.os.Process.myPid()}, thread=${Thread.currentThread().name}" }
        // TODO: Call startForeground()
        return START_NOT_STICKY
    }

    @SuppressLint("LogNotTimber")
    companion object {
        fun createReportIntent(context: Context) : Intent {//, crash: Crash): Intent {
            val intent = Intent(context, SendTelemetryService::class.java)
            //crash.fillIn(intent)
            Log.i("miguel-bd_", "SendTelemetryService.createReportIntent() called on process ${android.os.Process.myPid()}, thread=${Thread.currentThread().name}")
            Capture.Logger.logInfo { "SendTelemetryService.createReportIntent() called on process ${android.os.Process.myPid()}, thread=${Thread.currentThread().name}" }
            return intent
        }
    }
}