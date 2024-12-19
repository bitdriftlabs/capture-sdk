// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE
import android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK
import android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_TASK
import android.app.ApplicationStartInfo.LAUNCH_MODE_SINGLE_TOP
import android.app.ApplicationStartInfo.LAUNCH_MODE_STANDARD
import android.app.ApplicationStartInfo.STARTUP_STATE_ERROR
import android.app.ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN
import android.app.ApplicationStartInfo.STARTUP_STATE_STARTED
import android.app.ApplicationStartInfo.START_REASON_ALARM
import android.app.ApplicationStartInfo.START_REASON_BACKUP
import android.app.ApplicationStartInfo.START_REASON_BOOT_COMPLETE
import android.app.ApplicationStartInfo.START_REASON_BROADCAST
import android.app.ApplicationStartInfo.START_REASON_CONTENT_PROVIDER
import android.app.ApplicationStartInfo.START_REASON_JOB
import android.app.ApplicationStartInfo.START_REASON_LAUNCHER
import android.app.ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS
import android.app.ApplicationStartInfo.START_REASON_OTHER
import android.app.ApplicationStartInfo.START_REASON_PUSH
import android.app.ApplicationStartInfo.START_REASON_SERVICE
import android.app.ApplicationStartInfo.START_REASON_START_ACTIVITY
import android.app.ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE
import android.app.ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION
import android.app.ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME
import android.app.ApplicationStartInfo.START_TIMESTAMP_FORK
import android.app.ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN
import android.app.ApplicationStartInfo.START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME
import android.app.ApplicationStartInfo.START_TIMESTAMP_LAUNCH
import android.app.ApplicationStartInfo.START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE
import android.app.ApplicationStartInfo.START_TYPE_COLD
import android.app.ApplicationStartInfo.START_TYPE_HOT
import android.app.ApplicationStartInfo.START_TYPE_UNSET
import android.app.ApplicationStartInfo.START_TYPE_WARM
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import io.bitdrift.capture.Capture
import io.bitdrift.capture.Capture.Logger.sessionUrl
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.events.span.Span
import io.bitdrift.capture.events.span.SpanResult
import io.bitdrift.capture.timber.CaptureTree
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import papa.AppLaunchType
import papa.PapaEvent
import papa.PapaEventListener
import papa.PapaEventLogger
import papa.PreLaunchState
import timber.log.Timber
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A Java app entry point that initializes the Bitdrift Logger.
 */
class GradleTestApp : Application() {

    private var activitySpan: Span? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("Hello World!")
        initLogging()
        trackAppLaunch()
        trackAppLifecycle()
    }

    private fun initLogging() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val stringApiUrl = prefs.getString("apiUrl", null)
        val apiUrl = stringApiUrl?.toHttpUrlOrNull()
        if (apiUrl == null) {
            Log.e("GradleTestApp", "Failed to initialize bitdrift logger due to invalid API URL: $stringApiUrl")
            return
        }
        BitdriftInit.initBitdriftCaptureInJava(apiUrl, prefs.getString("apiKey", ""))
        // Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CaptureTree())
        Timber.i("Bitdrift Logger initialized with session_url=$sessionUrl")
    }

    private fun trackAppLaunch() {
        // ApplicationStartInfo
        if (Build.VERSION.SDK_INT >= 35) {
            val activityManager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appStartInfo = activityManager.getHistoricalProcessStartReasons(1).first()
//            activityManager.addApplicationStartInfoCompletionListener(ContextCompat.getMainExecutor(this)) { appStartInfo ->
                val appStartInfoFields = mapOf(
                    "startup_type" to appStartInfo.startType.toStartTypeText(),
                    "startup_state" to appStartInfo.startupState.toStartupStateText(),
                    "startup_launch_mode" to appStartInfo.launchMode.toLaunchModeText(),
                    "startup_was_forced_stopped" to appStartInfo.wasForceStopped().toString(),
                    "startup_reason" to appStartInfo.reason.toStartReasonText(),
                    "startup_intent_action" to appStartInfo.intent?.action.toString(),
                    "start_timestamp_launch_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_LAUNCH]?.toString() ?: "null"),
                    "start_timestamp_fork_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_FORK]?.toString() ?: "null"),
                    "start_timestamp_oncreate_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_APPLICATION_ONCREATE]?.toString() ?: "null"),
                    "start_timestamp_bind_application_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_BIND_APPLICATION]?.toString() ?: "null"),
                    "start_timestamp_first_frame_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_FIRST_FRAME]?.toString() ?: "null"),
                    "start_timestamp_fully_drawn_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_FULLY_DRAWN]?.toString() ?: "null"),
                    "start_timestamp_initial_renderthread_frame_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME]?.toString() ?: "null"),
                    "start_timestamp_surfaceflinger_composition_complete_ns" to (appStartInfo.startupTimestamps[START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE]?.toString() ?: "null")
                )
                Timber.d("ApplicationStartInfoCompletion event: $appStartInfoFields")
                Capture.Logger.logInfo(appStartInfoFields) { "ApplicationStartInfoCompletion" }
//            }
        }

        // Papa
        PapaEventListener.install { event ->
            Timber.d("Papa event: $event")
            when (event) {
                is PapaEvent.AppLaunch -> {
                    Capture.Logger.logInfo(
                        mapOf(
                            "preLaunchState" to event.preLaunchState.toString(),
                            "durationMs" to event.durationUptimeMillis.toString(),
                            "isSlowLaunch" to event.isSlowLaunch.toString(),
                            "trampolined" to event.trampolined.toString(),
                            "backgroundDurationMs" to event.invisibleDurationRealtimeMillis.toString(),
                            "startUptimeMs" to event.startUptimeMillis.toString(),
                        )
                    ) { "PapaEvent.AppLaunch" }
                    if (event.preLaunchState.launchType == AppLaunchType.COLD) {
                        Capture.Logger.logAppLaunchTTI(event.durationUptimeMillis.toDuration(DurationUnit.MILLISECONDS))
                    }
                }
                is PapaEvent.FrozenFrameOnTouch -> {
                    Capture.Logger.logInfo(
                        mapOf(
                            "activityName" to event.activityName,
                            "repeatTouchDownCount" to event.repeatTouchDownCount.toString(),
                            "handledElapsedMs" to event.deliverDurationUptimeMillis.toString(),
                            "frameElapsedMs" to event.dislayDurationUptimeMillis.toString(),
                            "pressedView" to event.pressedView.orEmpty(),
                        )
                    ) { "PapaEvent.FrozenFrameOnTouch" }
                }
                is PapaEvent.UsageError -> {
                    Capture.Logger.logInfo(
                        mapOf(
                            "debugMessage" to event.debugMessage,
                        )
                    ) { "PapaEvent.UsageError" }

                }
            }
        }
    }

    private fun Int.toStartTypeText(): String {
        return when (this) {
            START_TYPE_UNSET -> "START_TYPE_UNSET"
            START_TYPE_COLD -> "START_TYPE_COLD"
            START_TYPE_WARM -> "START_TYPE_WARM"
            START_TYPE_HOT -> "START_TYPE_HOT"
            else -> "UNKNOWN"
        }
    }

    private fun Int.toStartupStateText(): String {
        return when (this) {
            STARTUP_STATE_STARTED -> "STARTUP_STATE_STARTED"
            STARTUP_STATE_ERROR -> "STARTUP_STATE_ERROR"
            STARTUP_STATE_FIRST_FRAME_DRAWN -> "STARTUP_STATE_FIRST_FRAME_DRAWN"
            else -> "UNKNOWN"
        }
    }

    private fun Int.toLaunchModeText(): String {
        return when (this) {
            LAUNCH_MODE_STANDARD -> "LAUNCH_MODE_STANDARD"
            LAUNCH_MODE_SINGLE_TOP -> "LAUNCH_MODE_SINGLE_TOP"
            LAUNCH_MODE_SINGLE_INSTANCE -> "LAUNCH_MODE_SINGLE_INSTANCE"
            LAUNCH_MODE_SINGLE_TASK -> "LAUNCH_MODE_SINGLE_TASK"
            LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK -> "LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK"
            else -> "UNKNOWN"
        }
    }

    private fun Int.toStartReasonText(): String {
        return when (this) {
            START_REASON_ALARM -> "START_REASON_ALARM"
            START_REASON_BACKUP -> "START_REASON_BACKUP"
            START_REASON_BOOT_COMPLETE -> "START_REASON_BOOT_COMPLETE"
            START_REASON_BROADCAST -> "START_REASON_BROADCAST"
            START_REASON_CONTENT_PROVIDER -> "START_REASON_CONTENT_PROVIDER"
            START_REASON_JOB -> "START_REASON_JOB"
            START_REASON_LAUNCHER -> "START_REASON_LAUNCHER"
            START_REASON_LAUNCHER_RECENTS -> "START_REASON_LAUNCHER_RECENTS"
            START_REASON_OTHER -> "START_REASON_OTHER"
            START_REASON_PUSH -> "START_REASON_PUSH"
            START_REASON_SERVICE -> "START_REASON_SERVICE"
            START_REASON_START_ACTIVITY -> "START_REASON_START_ACTIVITY"
            else -> "UNKNOWN"
        }
    }

    private fun trackAppLifecycle(){
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                activitySpan = Capture.Logger.startSpan(
                    name = "Activity in Foreground",
                    level = LogLevel.INFO,
                    fields = mapOf("activity_name" to activity.localClassName))
            }

            override fun onActivityPaused(activity: Activity) {
                if (Random.nextBoolean()) {
                    activitySpan?.end(SpanResult.SUCCESS)
                } else {
                    activitySpan?.end(SpanResult.FAILURE)
                }
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })
    }
}
