package io.bitdrift.capture.replay.internal

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.PixelCopy
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.IScreenshotLogger
import io.bitdrift.capture.replay.ScreenshotCaptureMetrics
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

internal class ScreenshotCaptureEngine(
    private val errorHandler: ErrorHandler,
    private val logger: IScreenshotLogger,
    context: Context,
    mainThreadHandler: MainThreadHandler,
    private val windowManager: WindowManager,
    displayManager: DisplayManagers,
    private val executor: ExecutorService,
    private val clock: IClock = DefaultClock.getInstance(),
) {

    init {
        // Log to console all dependency addresses
        Log.d("miguel-Screenshot", "$errorHandler, $logger, $context, $mainThreadHandler, $windowManager, $displayManager, $clock, $executor")
    }

    fun captureScreenshot() {
        // TODO(murki): Use BuildVersionChecker after moving it to common module
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        val startTimeMs = clock.elapsedRealtime()
        val topView = windowManager.findRootViews().firstOrNull()
        if (topView == null) {
            // Log empty screenshot on unblock the rust engine caller
            logger.onScreenshotCaptured(ByteArray(0), ScreenshotCaptureMetrics(0, 0, 0))
            return
        }
        // TODO(murki): Reduce memory footprint by calling setDestinationBitmap() with a Bitmap.Config.RGB_565 instead
        //  of the default of Bitmap.Config.ARGB_8888
        val screenshotRequest = PixelCopy.Request.Builder.ofWindow(topView).build()
        PixelCopy.request(screenshotRequest, executor) { screenshotResult ->
            if (screenshotResult.status != PixelCopy.SUCCESS) {
                errorHandler.handleError("PixelCopy operation failed. Result.status=${screenshotResult.status.toStatusText()}", null)
                Log.w("miguel-Screenshot", "PixelCopy operation failed. Result.status=${screenshotResult.status.toStatusText()}")
                // Log empty screenshot on unblock the rust engine caller
                logger.onScreenshotCaptured(ByteArray(0), ScreenshotCaptureMetrics(0, 0, 0))
                return@request
            }

            val resultBitmap = screenshotResult.bitmap
            val metrics = ScreenshotCaptureMetrics(
                screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                screenshotAllocationByteCount = resultBitmap.allocationByteCount,
                screenshotByteCount = resultBitmap.byteCount
            )
            val stream = ByteArrayOutputStream()
            // Encode bitmap to bytearray while compressing it using JPEG=10 quality to match iOS
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 10, stream)
            resultBitmap.recycle()
            // TODO (murki): Figure out if there's a more memory efficient way to do this
            //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
            //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
            val screenshotBytes = stream.toByteArray()
            metrics.compressionTimeMs = clock.elapsedRealtime() - startTimeMs - metrics.screenshotTimeMs
            metrics.compressionByteCount = screenshotBytes.size
            Log.d("miguel-Screenshot", "Miguel-Finished screenshot operation on thread=${Thread.currentThread().name}, " +
                    "metrics=$metrics"
            )
            logger.onScreenshotCaptured(screenshotBytes, metrics)
        }
    }

    private fun Int.toStatusText(): String {
        return when (this) {
            PixelCopy.SUCCESS -> "SUCCESS"
            PixelCopy.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
            PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA"
            PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
            PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
            else -> "Unknown error: $this"
        }
    }
}