// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.Build
import android.util.Log
import android.view.Window
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.StateInfo
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.events.span.SpanField
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFieldValue
import io.bitdrift.capture.threading.CaptureDispatchers

/**
 * Reports Jank Frames and its duration in ms
 *
 * This will be a no-op when `client_feature.android.jank_stats_reporting` kill switch is disabled.
 *
 */
internal class JankStatsMonitor(
    private val logger: LoggerImpl,
    private val processLifecycleOwner: LifecycleOwner,
    private val runtime: Runtime,
    private val windowManager: IWindowManager,
    private val errorHandler: ErrorHandler,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
) : IEventListenerLogger,
    LifecycleEventObserver,
    JankStats.OnFrameListener {
    private var jankStats: JankStats? = null

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
        if (event == Lifecycle.Event.ON_RESUME) {
            windowManager.getCurrentWindow()?.let { setJankStatsForCurrentWindow(it) }
        } else if (event == Lifecycle.Event.ON_STOP) {
            stopCollection()
        }
    }

    override fun onFrame(volatileFrameData: FrameData) {
        if (volatileFrameData.isJank) {
            if (!runtime.isEnabled(RuntimeFeature.DROPPED_EVENTS_MONITORING)) {
                stopCollection()
                return
            }

            if (volatileFrameData.durationToMilli()
                < runtime.getConfigValue(RuntimeConfig.MIN_JANK_FRAME_THRESHOLD_MS)
            ) {
                // The Frame is considered as Jank but it didn't reached the min
                // threshold defined by MIN_JANK_FRAME_THRESHOLD_MS config
                return
            }

            // Below API 24 [onFrame(volatileFrameData)] call happens on the main thread
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                backgroundThreadHandler.runAsync { volatileFrameData.sendJankFrameData() }
            } else {
                // For >= 24 this happens on `FrameMetricsAggregator` thread
                volatileFrameData.sendJankFrameData()
            }
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
        jankStats = null
    }

    @WorkerThread
    private fun FrameData.sendJankFrameData() {
        val jankFrameLogDetails = this.getLogDetails()
        val map =             buildMap {
            put(SpanField.Key.DURATION, this@sendJankFrameData.durationToMilli().toString().toFieldValue())
            putAll(this@sendJankFrameData.states.toFields())
        }
        Log.w("miguel-jank", "Jank frame detected=${jankFrameLogDetails.message}, map=$map")
        logger.log(
            LogType.UX,
            jankFrameLogDetails.logLevel,
            map,
        ) { jankFrameLogDetails.message }
    }

    @WorkerThread
    private fun List<StateInfo>.toFields(): Map<String, FieldValue> {
        // Convert the list of StateInfo to a map of fields using StateInfo's key and value properties
        return this.associate { stateInfo ->
            stateInfo.key to stateInfo.value.toFieldValue()
        }
    }

            @WorkerThread
    private fun FrameData.getLogDetails(): JankFrameLogDetails =
        when (this.toJankType()) {
            JankFrameType.SLOW -> {
                JankFrameLogDetails(
                    LogLevel.WARNING,
                    DROPPED_FRAME_MESSAGE_ID,
                )
            }

            JankFrameType.FROZEN -> {
                JankFrameLogDetails(
                    LogLevel.ERROR,
                    DROPPED_FRAME_MESSAGE_ID,
                )
            }

            JankFrameType.ANR -> {
                JankFrameLogDetails(
                    LogLevel.ERROR,
                    ANR_MESSAGE_ID,
                )
            }
        }

    private fun FrameData.toJankType(): JankFrameType =
        if (this.durationToMilli() < runtime.getConfigValue(RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS)) {
            JankFrameType.SLOW
        } else if (this.durationToMilli() < runtime.getConfigValue(RuntimeConfig.ANR_FRAME_THRESHOLD_MS)) {
            JankFrameType.FROZEN
        } else {
            JankFrameType.ANR
        }

    private fun FrameData.durationToMilli(): Long {
        val durationInNano =
            when (this) {
                is FrameDataApi31 -> frameDurationTotalNanos
                is FrameDataApi24 -> frameDurationCpuNanos
                else -> {
                    frameDurationUiNanos
                }
            }
        return durationInNano / TO_MILLI
    }

    /**
     * The different type of Janks according to Play Vitals
     */
    private enum class JankFrameType {
        /**
         * Has a duration >= 16 ms and below [RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS]
         */
        SLOW,

        /**
         * With a duration between [RuntimeConfig.FROZEN_FRAME_THRESHOLD_MS] and below [RuntimeConfig.ANR_FRAME_THRESHOLD_MS]
         */
        FROZEN,

        /**
         * With a duration above [RuntimeConfig.ANR_FRAME_THRESHOLD_MS]
         */
        ANR,
    }

    private data class JankFrameLogDetails(
        val logLevel: LogLevel,
        val message: String,
    )

    internal companion object {
        private const val TO_MILLI = 1_000_000L
        private const val DROPPED_FRAME_MESSAGE_ID = "DroppedFrame"
        private const val ANR_MESSAGE_ID = "ANR"
        internal const val SCREEN_NAME_KEY = "_screen_name"
    }
}
