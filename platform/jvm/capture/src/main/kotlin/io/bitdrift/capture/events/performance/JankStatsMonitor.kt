package io.bitdrift.capture.events.performance

import android.view.Window
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.lifecycle.IWindowListener
import io.bitdrift.capture.providers.toFields

/**
 * Reports Jank Frames and its duration in ms
 *
 * This will be a no-op when `client_feature.android.jank_stats_reporting` kill switch is disabled
 */
internal class JankStatsMonitor(
    private val logger: LoggerImpl,
    runtime: Runtime,
) : IWindowListener,
    JankStats.OnFrameListener {
    private val shouldDisableMonitorViaKillSwitch = !runtime.isEnabled(RuntimeFeature.JANK_STATS_EVENTS)

    private var jankStats: JankStats? = null
    private var currentWindow: Window? = null

    override fun onFrame(volatileFrameData: FrameData) {
        if (volatileFrameData.shouldReportJankyFrame()) {
            sendJankStatData(volatileFrameData)
        }
    }

    /**
     * Called when we have a new window available
     */
    override fun onWindowAvailable(window: Window) {
        if (shouldDisableMonitorViaKillSwitch || currentWindow == window) {
            return
        }
        currentWindow = window
        jankStats = JankStats.createAndTrack(window, this)
    }

    /**
     * Called when Application is Stopped/Destroyed
     */
    override fun onWindowRemoved() {
        if (shouldDisableMonitorViaKillSwitch) {
            return
        }
        jankStats?.isTrackingEnabled = false
        jankStats = null
    }

    private fun sendJankStatData(frameData: FrameData) {
        val message = "$ROW_MESSAGE ${frameData.durationToMilli()} ms"
        val fields = mapOf(JANKY_FRAME_DURATION_KEY to "${frameData.durationToMilli()}")
        logger.log(LogType.LIFECYCLE, LogLevel.WARNING, fields.toFields()) { message }
    }

    private fun FrameData.durationToMilli(): Long = this.frameDurationUiNanos / TO_MILLI

    private fun FrameData.shouldReportJankyFrame(): Boolean =
        this.isJank && this.durationToMilli() >= MIN_THRESHOLD_JANKY_FRAME_DURATION_IN_MILLI

    private companion object {
        private const val TO_MILLI = 1000000L
        private const val MIN_THRESHOLD_JANKY_FRAME_DURATION_IN_MILLI = 16L
        private const val JANKY_FRAME_DURATION_KEY = "_jank_frame_duration"
        private const val ROW_MESSAGE = "Frozen frame with a duration of"
    }
}
