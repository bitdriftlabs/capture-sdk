package io.bitdrift.capture.replay.internal

import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.JankStats.OnFrameListener
import io.bitdrift.capture.Capture
import io.bitdrift.capture.common.BitdriftWindowManager

/**
 * Fake JankStateListener
 */
class JankStatsMonitor(
    bitdriftWindowManager: BitdriftWindowManager
) {

    private val jankStats: JankStats? by lazy {
        buildJankStats(bitdriftWindowManager)
    }

    /**
     * Start collecting jank frame stast
     */
    fun startCollection() {
        println("FRAN_TAG: starting collection with $jankStats")
        jankStats?.isTrackingEnabled = true
    }

    /**
     * Stops collecting
     */
    fun stopCollection() {
        jankStats?.isTrackingEnabled = false
    }

    private fun buildJankStats(bitdriftWindowManager: BitdriftWindowManager): JankStats? {
        println("FRAN_TAG: buildJankStats")
        try {
            val window = bitdriftWindowManager.findRootViews().firstOrNull()?.phoneWindow
            return if (window == null) {
                println("FRAN_TAG: window is null")
                null
            } else {
                println("FRAN_TAG: valid janks stats")
                JankStats.createAndTrack(window, OnFrameListenerImpl())
            }
        } catch (exception: Exception) {
            println("FRAN_TAG: exception" + exception)
        }
        return null
    }

    private class OnFrameListenerImpl() : OnFrameListener {

        override fun onFrame(volatileFrameData: FrameData) {
            val parsedData = volatileFrameData.parse()
            println("FRAN_TAG: " +parsedData)
            if (volatileFrameData.isJank) {
                val milli = volatileFrameData.frameDurationUiNanos.convertToMilli()
                Capture.Logger.logError(
                    mapOf("duration" to milli.toString())
                ) {
                    parsedData
                }
            }

        }

        private fun FrameData.parse(): String {
            val duration = this.frameDurationUiNanos.convertToMilli()

            return "Janky frame? ${this.isJank} .duration of $duration millisecond"
        }

        private fun Long.convertToMilli():String{
            return (this/100000L).toString()
        }
    }
}