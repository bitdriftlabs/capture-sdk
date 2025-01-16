// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.providers.toFields
import java.util.concurrent.ExecutorService

internal class AppLifecycleListenerLogger(
    private val logger: LoggerImpl,
    private val processLifecycleOwner: LifecycleOwner,
    private val activityManager: ActivityManager,
    private val runtime: Runtime,
    private val executor: ExecutorService,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IEventListenerLogger,
    LifecycleEventObserver {
    private val lifecycleEventNames =
        hashMapOf(
            Lifecycle.Event.ON_CREATE to "AppCreate",
            Lifecycle.Event.ON_START to "AppStart",
            Lifecycle.Event.ON_RESUME to "AppResume",
            Lifecycle.Event.ON_PAUSE to "AppPause",
            Lifecycle.Event.ON_STOP to "AppStop",
            Lifecycle.Event.ON_DESTROY to "AppDestroy",
            Lifecycle.Event.ON_ANY to "AppAny",
        )

    override fun start() {
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.addObserver(this)
        }
    }

    override fun stop() {
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.removeObserver(this)
        }
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        executor.execute {
            if (!runtime.isEnabled(RuntimeFeature.APP_LIFECYCLE_EVENTS)) {
                return@execute
            }

            val fields = if (event == Lifecycle.Event.ON_CREATE && (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
                extractAppStartInfoFields()
            } else {
                null
            } ?: emptyMap()
            // refer to lifecycle states https://developer.android.com/topic/libraries/architecture/lifecycle#lc
            logger.log(
                LogType.LIFECYCLE,
                LogLevel.INFO,
                fields.toFields(),
            ) { "${lifecycleEventNames[event]}" }

            if (event == Lifecycle.Event.ON_STOP) {
                logger.flush(false)
            }
        }
    }

    @RequiresApi(35)
    private fun extractAppStartInfoFields(): Map<String, String>? {
       val appStartInfo = activityManager.getHistoricalProcessStartReasons(1).firstOrNull() ?: return null
        val ts = StartupTimestamps.fromMap(appStartInfo.startupTimestamps)
        Log.i("ApplicationStartInfo", "timestamps: $ts")
        return mapOf(
            "startup_type" to appStartInfo.startType.toStartTypeText(),
            "startup_state" to appStartInfo.startupState.toStartupStateText(),
            "startup_launch_mode" to appStartInfo.launchMode.toLaunchModeText(),
            "startup_was_forced_stopped" to appStartInfo.wasForceStopped().toString(),
            "startup_reason" to appStartInfo.reason.toStartReasonText(),
            "startup_intent_action" to appStartInfo.intent?.action.toString(),
        )
    }

    private fun Int.toStartTypeText(): String {
        return when (this) {
            ApplicationStartInfo.START_TYPE_UNSET -> "START_TYPE_UNSET"
            ApplicationStartInfo.START_TYPE_COLD  -> "START_TYPE_COLD"
            ApplicationStartInfo.START_TYPE_WARM  -> "START_TYPE_WARM"
            ApplicationStartInfo.START_TYPE_HOT   -> "START_TYPE_HOT"
            else                                  -> "UNKNOWN"
        }
    }

    private fun Int.toStartupStateText(): String {
        return when (this) {
            ApplicationStartInfo.STARTUP_STATE_STARTED           -> "STARTUP_STATE_STARTED"
            ApplicationStartInfo.STARTUP_STATE_ERROR             -> "STARTUP_STATE_ERROR"
            ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN -> "STARTUP_STATE_FIRST_FRAME_DRAWN"
            else                                                 -> "UNKNOWN"
        }
    }

    private fun Int.toLaunchModeText(): String {
        return when (this) {
            ApplicationStartInfo.LAUNCH_MODE_STANDARD                 -> "LAUNCH_MODE_STANDARD"
            ApplicationStartInfo.LAUNCH_MODE_SINGLE_TOP               -> "LAUNCH_MODE_SINGLE_TOP"
            ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE          -> "LAUNCH_MODE_SINGLE_INSTANCE"
            ApplicationStartInfo.LAUNCH_MODE_SINGLE_TASK              -> "LAUNCH_MODE_SINGLE_TASK"
            ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK -> "LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK"
            else                                                      -> "UNKNOWN"
        }
    }

    private fun Int.toStartReasonText(): String {
        return when (this) {
            ApplicationStartInfo.START_REASON_ALARM            -> "START_REASON_ALARM"
            ApplicationStartInfo.START_REASON_BACKUP           -> "START_REASON_BACKUP"
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE    -> "START_REASON_BOOT_COMPLETE"
            ApplicationStartInfo.START_REASON_BROADCAST        -> "START_REASON_BROADCAST"
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> "START_REASON_CONTENT_PROVIDER"
            ApplicationStartInfo.START_REASON_JOB              -> "START_REASON_JOB"
            ApplicationStartInfo.START_REASON_LAUNCHER         -> "START_REASON_LAUNCHER"
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> "START_REASON_LAUNCHER_RECENTS"
            ApplicationStartInfo.START_REASON_OTHER            -> "START_REASON_OTHER"
            ApplicationStartInfo.START_REASON_PUSH             -> "START_REASON_PUSH"
            ApplicationStartInfo.START_REASON_SERVICE          -> "START_REASON_SERVICE"
            ApplicationStartInfo.START_REASON_START_ACTIVITY   -> "START_REASON_START_ACTIVITY"
            else                                               -> "UNKNOWN"
        }
    }

    @RequiresApi(35)
    data class StartupTimestamps(
        val applicationOnCreate: Long? = null,
        val bindApplication: Long? = null,
        val firstFrame: Long? = null,
        val fork: Long? = null,
        val fullyDrawn: Long? = null,
        val initialRenderThreadFrame: Long? = null,
        val launch: Long? = null,
        val reservedRangeDeveloper: Long? = null,
        val reservedRangeDeveloperStart: Long? = null,
        val reservedRangeSystem: Long? = null,
        val surfaceFlingerCompositionComplete: Long? = null
    ) {
        companion object {
            fun fromMap(timestampMap: Map<Int, Long>): StartupTimestamps = StartupTimestamps(
                applicationOnCreate = timestampMap[ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE],
                bindApplication = timestampMap[ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION],
                firstFrame = timestampMap[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME],
                fork = timestampMap[ApplicationStartInfo.START_TIMESTAMP_FORK],
                fullyDrawn = timestampMap[ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN],
                initialRenderThreadFrame = timestampMap[ApplicationStartInfo.START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME],
                launch = timestampMap[ApplicationStartInfo.START_TIMESTAMP_LAUNCH],
                reservedRangeDeveloper = timestampMap[ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_DEVELOPER],
                reservedRangeDeveloperStart = timestampMap[ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_DEVELOPER_START],
                reservedRangeSystem = timestampMap[ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_SYSTEM],
                surfaceFlingerCompositionComplete = timestampMap[ApplicationStartInfo.START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE]
            )
        }
    }
}
