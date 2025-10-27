// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.startup

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import io.bitdrift.capture.Capture

object AppStartInfoLogger {
    fun register(app: Application) {
        if (Build.VERSION.SDK_INT < 35) return
        val activityManager: ActivityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.addApplicationStartInfoCompletionListener(ContextCompat.getMainExecutor(app)) { appStartInfo ->
            val appStartInfoFields =
                mapOf(
                    "startup_type" to appStartInfo.startType.toStartTypeText(),
                    "startup_state" to appStartInfo.startupState.toStartupStateText(),
                    "startup_launch_mode" to appStartInfo.launchMode.toLaunchModeText(),
                    "startup_was_forced_stopped" to appStartInfo.wasForceStopped().toString(),
                    "startup_reason" to appStartInfo.reason.toStartReasonText(),
                    "startup_intent_action" to appStartInfo.intent?.action.toString(),
                )
            Capture.Logger.logInfo(appStartInfoFields) { "ApplicationStartInfoCompletion" }
        }
    }
}
