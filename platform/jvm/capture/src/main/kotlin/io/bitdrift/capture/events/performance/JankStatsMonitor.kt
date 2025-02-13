// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.Build
import android.view.Window
import androidx.annotation.WorkerThread
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.ILifecycleWindowListener
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.threading.CaptureDispatchers

/**
 * Reports Jank Frames and its duration in ms
 *
 * This will be a no-op when `client_feature.android.jank_stats_reporting` kill switch is disabled.
 *
 */
internal class JankStatsMonitor(
    private val logger: LoggerImpl,
    private val runtime: Runtime,
    private val errorHandler: ErrorHandler,
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
) : ILifecycleWindowListener,
    JankStats.OnFrameListener {
    private var jankStats: JankStats? = null

    override fun onWindowAvailable(window: Window) {
        stopCollection()

        if (runtime.isEnabled(RuntimeFeature.JANK_STATS_EVENTS)) {
            setJankStatsForCurrentWindow(window)
        }
    }

    override fun onWindowRemoved() {
        stopCollection()
    }

    override fun onFrame(volatileFrameData: FrameData) {
        if (!runtime.isEnabled(RuntimeFeature.JANK_STATS_EVENTS)) {
            stopCollection()
            return
        }

        if (volatileFrameData.isJank) {
            // Below API 24 [onFrame(volatileFrameData)] call happens on the main thread
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                backgroundThreadHandler.runAsync { volatileFrameData.sendJankFrameData() }
            } else {
                // For >= 24 this happens on `FrameMetricsAggregator` thread
                volatileFrameData.sendJankFrameData()
            }
        }
    }

    @WorkerThread
    private fun FrameData.sendJankFrameData() {
        val fields =
            mapOf(
                DURATION_IN_MILLI_LOG_KEY to "${this.durationToMilli()}",
                FRAME_TYPE_LOG_KEY to "${this.toType()}",
            ).toFields()
        logger.log(LogType.UX, LogLevel.ERROR, fields) { "JankFrame" }
    }

    private fun setJankStatsForCurrentWindow(window: Window) {
        try {
            jankStats = JankStats.createAndTrack(window, this)
            jankStats?.jankHeuristicMultiplier = DEFAULT_JANK_HEURISTICS_MULTIPLIER
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

    private fun FrameData.durationToMilli(): Long = this.frameDurationUiNanos / TO_MILLI

    private fun FrameData.toType(): JankFrameType =
        if (this.durationToMilli() < FROZEN_FRAME_THRESHOLD_MILLI) {
            JankFrameType.SLOW
        } else if (this.durationToMilli() < ANR_FRAME_THRESHOLD_MILLI) {
            JankFrameType.FROZEN
        } else {
            JankFrameType.ANR
        }

    private companion object {
        private const val TO_MILLI = 1_000_000L
        private const val DEFAULT_JANK_HEURISTICS_MULTIPLIER = 2.0F
        private const val DURATION_IN_MILLI_LOG_KEY = "_jank_frame_duration_ms"
        private const val FRAME_TYPE_LOG_KEY = "_jank_frame_type"
        private const val FROZEN_FRAME_THRESHOLD_MILLI = 700L
        private const val ANR_FRAME_THRESHOLD_MILLI = 5000L
    }
}

/**
 * The different type of Janks according to Play Vitals
 */
enum class JankFrameType(
    private val displayName: String,
) {
    /**
     * Has a duration below 700 ms
     */
    SLOW("SLOW"),

    /**
     * With a duration between 700 ms and below 5000 ms
     */
    FROZEN("FROZEN"),

    /**
     * With a duration above 5000 ms
     */
    ANR("ANR"), ;

    override fun toString(): String = displayName
}
