@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import androidx.annotation.RequiresApi
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.IScreenshotLogger
import io.bitdrift.capture.replay.ScreenshotCaptureMetrics
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ExecutorService

internal class ScreenshotCaptureEngine(
    private val errorHandler: ErrorHandler,
    private val logger: IScreenshotLogger,
    context: Context,
    private val mainThreadHandler: MainThreadHandler,
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
        val startTimeMs = clock.elapsedRealtime()
        val topView = windowManager.findRootViews().firstOrNull()
        if (topView == null) {
            logEmpty()
            return
        }
        // TODO(murki): Use BuildVersionChecker after moving it to common module
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            modernPixelCopySnapshot(topView, startTimeMs)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pixelCopySnapshot(topView, startTimeMs)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun modernPixelCopySnapshot(topView: View, startTimeMs: Long) {
        // TODO(murki): Reduce memory footprint by calling setDestinationBitmap() with a Bitmap.Config.RGB_565 instead
        //  of the default of Bitmap.Config.ARGB_8888
        val screenshotRequest = PixelCopy.Request.Builder.ofWindow(topView).build()
        // TODO(murki): Handle errors
        PixelCopy.request(screenshotRequest, executor) { screenshotResult ->
            if (screenshotResult.status != PixelCopy.SUCCESS) {
                errorHandler.handleError("PixelCopy operation failed. Result.status=${screenshotResult.status.toStatusText()}", null)
                Log.w("miguel-Screenshot", "PixelCopy operation failed. Result.status=${screenshotResult.status.toStatusText()}")
                logEmpty()
                return@request
            }

            val resultBitmap = screenshotResult.bitmap
            val metrics = ScreenshotCaptureMetrics(
                screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                screenshotAllocationByteCount = resultBitmap.allocationByteCount,
                screenshotByteCount = resultBitmap.byteCount
            )
            val screenshotBytes = compressScreenshot(resultBitmap)
            logger.onScreenshotCaptured(screenshotBytes, metrics)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun pixelCopySnapshot(root: View, startTimeMs: Long) {
        if (root.width <= 0 || root.height <= 0 || !root.isShown) {
            // "Root view is invalid, not capturing screenshot"
            return
        }

        val window = root.phoneWindow
        if (window == null) {
            // "Window is invalid, not capturing screenshot"
            return
        }

        val bitmap = Bitmap.createBitmap(
            root.width,
            root.height,
            Bitmap.Config.RGB_565
        )
        PixelCopy.request(
            window,
            bitmap,
            { copyResult: Int ->
                if (copyResult != PixelCopy.SUCCESS) {
                    // "PixelCopy operation failed, not capturing screenshot"
                    bitmap.recycle()
                    return@request
                }
                val metrics = ScreenshotCaptureMetrics(
                    screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                    screenshotAllocationByteCount = bitmap.allocationByteCount,
                    screenshotByteCount = bitmap.byteCount
                )
                val screenshotBytes = compressScreenshot(bitmap)
                logger.onScreenshotCaptured(screenshotBytes, metrics)
            },
            mainThreadHandler.mainHandler,
        )
    }


    private fun compressScreenshot(resultBitmap: Bitmap): ByteArray {
        // TODO(murki): Handle errors
        val stream = ByteArrayOutputStream()
        // Encode bitmap to bytearray while compressing it using JPEG=10 quality to match iOS
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 10, stream)
        resultBitmap.recycle()
//        PipedInputStream().use { inputStream ->
//            PipedOutputStream(inputStream).use { outputStream ->
//                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 10, outputStream)
//                resultBitmap.recycle()
//            }
//            return inputStream.readBytes()
//        }
        // TODO(murki): Figure out if there's a more memory efficient way to do this
        //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
        //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
        //  and https://code.luasoftware.com/tutorials/android/android-convert-outputstream-to-inputstream
        return stream.toByteArray() // toByteArray will trigger copy, thus double memory usage
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