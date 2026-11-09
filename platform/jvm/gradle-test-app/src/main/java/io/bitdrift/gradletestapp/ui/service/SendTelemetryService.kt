// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import timber.log.Timber

class SendTelemetryService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        Timber.i("SendTelemetryService.onBind() called on process ${Process.myPid()}, thread=${Thread.currentThread().name}")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("SendTelemetryService.onStartCommand() called on process ${Process.myPid()}, thread=${Thread.currentThread().name}")

        val channelId = "send_telemetry_channel"
        val notificationId = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Send Telemetry", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sending Telemetry")
            .setContentText("Processing...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(notificationId, notification)

        // Simulate telemetry sending work
        Thread.sleep(500)
        // Stopping the service after work is done
        stopSelf()

        return START_NOT_STICKY
    }

    companion object {
        fun createReportIntent(context: Context) : Intent {//, crash: Crash): Intent {
            val intent = Intent(context, SendTelemetryService::class.java)
            Timber.i("SendTelemetryService.createReportIntent() called on process ${Process.myPid()}, thread=${Thread.currentThread().name}")
            return intent
        }
    }
}