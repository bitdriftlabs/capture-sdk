package io.bitdrift.capture.replay

import kotlin.time.Duration

interface IScreenshotLogger {
    fun onScreenshotCaptured(compressedScreen: ByteArray, durationMs: Long)
}