package io.bitdrift.capture.replay

import kotlin.time.Duration

interface IScreenshotLogger : IInternalLogger {
    fun onScreenshotCaptured(compressedScreen: ByteArray, metrics: ScreenshotCaptureMetrics)
}