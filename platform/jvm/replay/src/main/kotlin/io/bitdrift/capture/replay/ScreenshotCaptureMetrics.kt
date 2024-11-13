package io.bitdrift.capture.replay

data class ScreenshotCaptureMetrics(
    val screenshotTimeMs: Long,
    val screenshotAllocationByteCount: Int,
    val screenshotByteCount: Int,
    var compressionTimeMs: Long = 0,
    var compressionByteCount: Int = 0,
) {
    private val totalDurationMs: Long
        get() = screenshotTimeMs + compressionTimeMs

    fun toMap(): Map<String, String> {
        return mapOf(
            "screenshot_time_ms" to screenshotTimeMs.toString(),
            "screenshot_allocation_byte_count" to screenshotAllocationByteCount.toString(),
            "screenshot_byte_count" to screenshotByteCount.toString(),
            "compression_time_ms" to compressionTimeMs.toString(),
            "compression_byte_count" to compressionByteCount.toString(),
            "total_duration_ms" to totalDurationMs.toString(),
        )
    }
}
