@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
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
    private val mainThreadHandler: MainThreadHandler,
    private val windowManager: WindowManager,
    private val executor: ExecutorService,
    private val clock: IClock = DefaultClock.getInstance(),
) {

    init {
        // Log to console all dependency addresses
        Log.d("miguel-Screenshot", "$errorHandler, $logger, $context, $mainThreadHandler, $windowManager, $clock, $executor")
    }

    fun captureScreenshot() {
        val startTimeMs = clock.elapsedRealtime()
        val rootView = windowManager.findRootViews().firstOrNull()
        if (rootView == null || rootView.width <= 0 || rootView.height <= 0 || !rootView.isShown) {
            logger.logErrorInternal("Screenshot triggered: Root view is invalid, skipping capture")
            logEmpty()
            return
        }
        // TODO(murki): Use BuildVersionChecker after moving it to common module
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            modernPixelCopySnapshot(rootView, startTimeMs)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pixelCopySnapshot(rootView, startTimeMs)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun modernPixelCopySnapshot(topView: View, startTimeMs: Long) {
        // TODO(murki): Reduce memory footprint by calling setDestinationBitmap() with a Bitmap.Config.RGB_565 instead
        //  of the default of Bitmap.Config.ARGB_8888
        val screenshotRequest = PixelCopy.Request.Builder.ofWindow(topView).build()
        // TODO(murki): Handle errors
        PixelCopy.request(screenshotRequest, executor) { screenshotResult ->
            val resultBitmap = screenshotResult.bitmap
            if (handleErrorResult(screenshotResult.status, resultBitmap)) return@request

            val metrics = ScreenshotCaptureMetrics(
                screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                screenshotAllocationByteCount = resultBitmap.allocationByteCount,
                screenshotByteCount = resultBitmap.byteCount
            )
            val screenshotBytes = compressScreenshot(resultBitmap)
            metrics.compressionTimeMs = clock.elapsedRealtime() - startTimeMs - metrics.screenshotTimeMs
            metrics.compressionByteCount = screenshotBytes.size
            logger.onScreenshotCaptured(screenshotBytes, metrics)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pixelCopySnapshot(root: View, startTimeMs: Long) {
        val window = root.phoneWindow
        if (window == null) {
            logger.logErrorInternal("Screenshot triggered: Phone window invalid, skipping capture")
            logEmpty()
            return
        }

        //TODO(murki): Fix threading
        val resultBitmap = Bitmap.createBitmap(
            root.width,
            root.height,
            Bitmap.Config.RGB_565
        )
        PixelCopy.request(
            window,
            resultBitmap,
            { screenshotResultStatus: Int ->
                if (handleErrorResult(screenshotResultStatus, resultBitmap)) return@request

                val metrics = ScreenshotCaptureMetrics(
                    screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                    screenshotAllocationByteCount = resultBitmap.allocationByteCount,
                    screenshotByteCount = resultBitmap.byteCount
                )
                val screenshotBytes = compressScreenshot(resultBitmap)
                metrics.compressionTimeMs = clock.elapsedRealtime() - startTimeMs - metrics.screenshotTimeMs
                metrics.compressionByteCount = screenshotBytes.size
                logger.onScreenshotCaptured(screenshotBytes, metrics)
            },
            mainThreadHandler.mainHandler,
        )
    }

    private fun handleErrorResult(screenshotResultStatus: Int, resultBitmap: Bitmap): Boolean {
        if (screenshotResultStatus != PixelCopy.SUCCESS) {
            Log.e("miguel-Screenshot", "PixelCopy operation failed. Result.status=${screenshotResultStatus.toStatusText()}")
            resultBitmap.recycle()
            errorHandler.handleError("Screenshot triggered: PixelCopy operation failed. Result.status=${screenshotResultStatus.toStatusText()}", null)
            logger.logErrorInternal("Screenshot triggered: PixelCopy operation failed. Result.status=${screenshotResultStatus.toStatusText()}")
            logEmpty()
            return true
        }
        return false
    }

    private fun compressScreenshot(resultBitmap: Bitmap): ByteArray {
        val result = try {
            ByteArrayOutputStream().use { outStream ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 10, outStream)
                resultBitmap.recycle()
                // TODO(murki): Figure out if there's a more memory efficient way to do this
                //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
                //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
                //  and https://code.luasoftware.com/tutorials/android/android-convert-outputstream-to-inputstream
                outStream.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("miguel-Screenshot", "Failed to compress screenshot", e)
            resultBitmap.recycle()
            errorHandler.handleError("Screenshot triggered: Failed to compress screenshot", e)
            logger.logErrorInternal("Screenshot triggered: Failed to compress screenshot", e)
            ByteArray(0)
        }

        return result
    }

    private fun logEmpty() {
        // Log empty screenshot on unblock the rust engine caller
        logger.onScreenshotCaptured(ByteArray(0), ScreenshotCaptureMetrics(0, 0, 0))
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