package io.bitdrift.capture.replay

interface IScreenshotLogger : IInternalLogger {
    fun onScreenshotCaptured(compressedScreen: ByteArray, metrics: ScreenshotCaptureMetrics)
}
