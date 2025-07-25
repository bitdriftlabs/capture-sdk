// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Window
import androidx.annotation.OpenForTesting
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.metrics.performance.StateInfo
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.events.span.SpanField
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import java.util.concurrent.TimeUnit

/**
 * Reports Jank Frames and its duration in ms
 *
 * This will be a no-op when `client_feature.android.jank_stats_reporting` kill switch is disabled.
 *
 */
internal class JankStatsMonitor(
    private val application: Application,
    private val logger: LoggerImpl,
    private val processLifecycleOwner: LifecycleOwner,
    private val runtime: Runtime,
    private val windowManager: IWindowManager,
    private val errorHandler: ErrorHandler,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IEventListenerLogger,
    Application.ActivityLifecycleCallbacks,
    LifecycleEventObserver,
    JankStats.OnFrameListener {
    @VisibleForTesting
    internal var jankStats: JankStats? = null
    private var performanceMetricsStateHolder: PerformanceMetricsState.Holder? = null

    override fun start() {
        mainThreadHandler.run {
            // TODO(FranAguilera): BIT-4785. To improve detection of Application/Activity lifecycles
            processLifecycleOwner.lifecycle.addObserver(this)
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun stop() {
        stopCollection()
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.removeObserver(this)
            application.unregisterActivityLifecycleCallbacks(this)
        }
    }

    override fun onFrame(volatileFrameData: FrameData) {
        if (!volatileFrameData.isJank) {
            return
        }
        if (!runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
            stopCollection()
            return
        }

        // From the docs: note that the FrameData object sent in the callback is reused on every frame
        // to prevent having to allocate new objects for data reporting.
        // This means that you must copy and cache that data elsewhere since that object should be considered stale
        // and obsolete as soon as the callback returns.
        val frameData = volatileFrameData.copy()

        val durationNano = frameData.toDurationNano()
        val durationMillis = frameData.toDurationMillis()
        if (durationNano < 0 || durationMillis > ERROR_DURATION_THRESHOLD_MILLIS) {
            val errorMessage =
                "Unexpected frame duration. durationInNano: $durationNano." +
                    " durationMillis: $durationMillis"
            errorHandler.handleError(errorMessage)
            return
        }

        if (durationMillis < runtime.getConfigValue(RuntimeConfig.MIN_JANK_FRAME_THRESHOLD_MS)) {
            // The Frame is considered as Jank but it didn't reached the min
            // threshold defined by MIN_JANK_FRAME_THRESHOLD_MS config
            return
        }

        frameData.sendJankFrameData()
    }

    /**
     * [LifecycleEventObserver] callback to determine when Application is first created
     */
    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        if (!runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
            return
        }
        if (event == Lifecycle.Event.ON_CREATE) {
            windowManager.getCurrentWindow()?.let {
                setJankStatsForCurrentWindow(it)
                // We are done detecting initial Application ON_CREATE, we don't need to listen anymore
                processLifecycleOwner.lifecycle.removeObserver(this)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
            return
        }
        setJankStatsForCurrentWindow(activity.window)
    }

    override fun onActivityPaused(activity: Activity) {
        stopCollection()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        // no-op
    }

    override fun onActivityStarted(activity: Activity) {
        // no-op
    }

    override fun onActivityStopped(activity: Activity) {
        // no-op
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
        // no-op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // no-op
    }

    fun trackScreenNameChanged(screenName: String) {
        if (runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
            performanceMetricsStateHolder?.state?.putState(SCREEN_NAME_KEY, screenName)
        }
    }

    @UiThread
    private fun setJankStatsForCurrentWindow(window: Window) {
        try {
            if (!runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
                stopCollection()
                return
            }
            jankStats = JankStats.createAndTrack(window, this)
            performanceMetricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(window.decorView.rootView)
            jankStats?.jankHeuristicMultiplier = runtime.getConfigValue(RuntimeConfig.JANK_FRAME_HEURISTICS_MULTIPLIER).toFloat()
        } catch (illegalStateException: IllegalStateException) {
            errorHandler.handleError(
                "Couldn't create JankStats instance",
                illegalStateException,
            )
        }
    }

    private fun stopCollection() {
        jankStats?.isTrackingEnabled = false
        performanceMetricsStateHolder?.state?.removeState(SCREEN_NAME_KEY)
        jankStats = null
        performanceMetricsStateHolder = null
    }

    @WorkerThread
    private fun FrameData.sendJankFrameData() {
        val jankFrameType = toJankType()
        logger.log(
            LogType.UX,
            jankFrameType.logLevel,
            buildMap {
                put(SpanField.Key.DURATION, toDurationMillis().toString().toFieldValue())
                put(DROPPED_FRAME_TYPE_KEY, jankFrameType.fieldValue)
                putAll(this@sendJankFrameData.states.toFields())
            },
        ) { DROPPED_FRAME_MESSAGE_ID }
    }

    @WorkerThread
    private fun List<StateInfo>.toFields(): Map<String, FieldValue> {
        // Convert the list of StateInfo to a map of fields using StateInfo's key and value properties
        return this.associate { stateInfo ->
            stateInfo.key to stateInfo.value.toFieldValue()
        }
    }

    private fun FrameData.toJankType(): JankFrameType {
        val durationMillis = this.toDurationMillis()
        return if (durationMillis < runtime.getConfigValue(RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS)) {
            JankFrameType.SLOW
        } else if (durationMillis < runtime.getConfigValue(RuntimeConfig.ANR_FRAME_THRESHOLD_MS)) {
            JankFrameType.FROZEN
        } else {
            JankFrameType.ANR
        }
    }

    private fun FrameData.toDurationMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this.toDurationNano())

    private fun FrameData.toDurationNano(): Long =
        when (this) {
            is FrameDataApi31 -> frameDurationTotalNanos
            is FrameDataApi24 -> frameDurationCpuNanos
            else -> {
                frameDurationUiNanos
            }
        }

    /**
     * The different type of Janks according to Play Vitals
     */
    @OpenForTesting
    internal enum class JankFrameType(
        val logLevel: LogLevel,
        val fieldValue: FieldValue,
    ) {
        /**
         * Has a duration >= 16 ms and below [RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS]
         */
        SLOW(LogLevel.WARNING, "Slow".toFieldValue()),

        /**
         * With a duration between [RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS] and below [RuntimeConfig.ANR_FRAME_THRESHOLD_MS]
         */
        FROZEN(LogLevel.ERROR, "Frozen".toFieldValue()),

        /**
         * With a duration above [RuntimeConfig.ANR_FRAME_THRESHOLD_MS]
         */
        ANR(LogLevel.ERROR, "ANR".toFieldValue()),
    }

    private companion object {
        private const val ERROR_DURATION_THRESHOLD_MILLIS = 100_000_000L
        private const val DROPPED_FRAME_MESSAGE_ID = "DroppedFrame"
        private const val SCREEN_NAME_KEY = "_screen_name"
        private const val DROPPED_FRAME_TYPE_KEY = "_frame_issue_type"
    }
}
