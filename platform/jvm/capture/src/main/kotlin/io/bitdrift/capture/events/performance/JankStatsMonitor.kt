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
import io.bitdrift.capture.events.span.SpanField
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

    private fun setJankStatsForCurrentWindow(window: Window) {
        try {
            jankStats = JankStats.createAndTrack(window, this)

            // BIT-4665 To update Runtime to provide heuristics value from config
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

    @WorkerThread
    private fun FrameData.sendJankFrameData() {
        val jankFrameLogDetails = this.getLogDetails()
        logger.log(
            LogType.UX,
            jankFrameLogDetails.logLevel,
            mapOf(SpanField.Key.DURATION to this.durationToMilli().toString().toFieldValue()),
        ) { jankFrameLogDetails.message }
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
        if (this.durationToMilli() < FROZEN_FRAME_THRESHOLD_MS) {
            JankFrameType.SLOW
        } else if (this.durationToMilli() < ANR_FRAME_THRESHOLD_MS) {
            JankFrameType.FROZEN
        } else {
            JankFrameType.ANR
        }

    private fun FrameData.durationToMilli(): Long = this.frameDurationUiNanos / TO_MILLI

    /**
     * The different type of Janks according to Play Vitals
     */
    private enum class JankFrameType {
        /**
         * Has a duration >= 16 ms and below 700 ms
         */
        SLOW,

        /**
         * With a duration between 700 ms and below 5000 ms
         */
        FROZEN,

        /**
         * With a duration above 5000 ms
         */
        ANR,
    }

    private data class JankFrameLogDetails(
        val logLevel: LogLevel,
        val message: String,
    )

    private companion object {
        private const val TO_MILLI = 1_000_000L
        private const val DROPPED_FRAME_MESSAGE_ID = "DroppedFrame"
        private const val ANR_MESSAGE_ID = "ANR"
        private const val DEFAULT_JANK_HEURISTICS_MULTIPLIER = 2.0F
        private const val FROZEN_FRAME_THRESHOLD_MS = 700L
        private const val ANR_FRAME_THRESHOLD_MS = 5000L
    }
}
