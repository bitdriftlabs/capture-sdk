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
        try {
            val startTimeMs = clock.elapsedRealtime()
            val rootView = windowManager.findRootViews().firstOrNull()
            if (rootView == null || rootView.width <= 0 || rootView.height <= 0 || !rootView.isShown) {
                finishOnError(expected = true, "Screenshot triggered: Root view is invalid, skipping capture")
                return
            }
            // TODO(murki): Use BuildVersionChecker after moving it to common module
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                modernPixelCopySnapshot(rootView, startTimeMs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopySnapshot(rootView, startTimeMs)
            } else {
                logger.logErrorInternal("Screenshot triggered: Unsupported Android version=${Build.VERSION.SDK_INT}, skipping capture")
                // We purposefully do not log an empty screenshot here to avoid spamming requests if we're never gonna be able to handle them
            }
        } catch (e: Exception) {
            finishOnError(
                expected = false,
                "Screenshot triggered: skipping capture. Exception=${e.message}",
                e
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun modernPixelCopySnapshot(topView: View, startTimeMs: Long) {
        // TODO(murki): Reduce memory footprint by calling setDestinationBitmap() with a Bitmap.Config.RGB_565 instead
        //  of the default of Bitmap.Config.ARGB_8888
        val screenshotRequest = PixelCopy.Request.Builder.ofWindow(topView).build()
        PixelCopy.request(screenshotRequest, executor) { screenshotResult ->
            val resultBitmap = screenshotResult.bitmap
            try {
                if (screenshotResult.status != PixelCopy.SUCCESS) {
                    finishOnError(
                        expected = false,
                        "Screenshot triggered: PixelCopy operation failed. Result.status=${screenshotResult.status}",
                    )
                    return@request
                }

                val metrics = ScreenshotCaptureMetrics(
                    screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                    screenshotAllocationByteCount = resultBitmap.allocationByteCount,
                    screenshotByteCount = resultBitmap.byteCount
                )
                val screenshotBytes = compressScreenshot(resultBitmap)
                metrics.compressionTimeMs = clock.elapsedRealtime() - startTimeMs - metrics.screenshotTimeMs
                metrics.compressionByteCount = screenshotBytes.size
                logger.onScreenshotCaptured(screenshotBytes, metrics)
            } catch (e: Exception) {
                finishOnError(
                    expected = false,
                    "Screenshot triggered: PixelCopy operation failed. Exception=${e.message}",
                    e
                )
            } finally {
                resultBitmap.recycle()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pixelCopySnapshot(root: View, startTimeMs: Long) {
        val window = root.phoneWindow
        if (window == null) {
            finishOnError(expected = true, "Screenshot triggered: Phone window invalid, skipping capture")
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
                if (screenshotResultStatus != PixelCopy.SUCCESS) {
                    finishOnError(
                        expected = false,
                        "Screenshot triggered: PixelCopy operation failed. Result.status=${screenshotResultStatus.toStatusText()}",
                    )
                    return@request
                }

                try {
                    val metrics = ScreenshotCaptureMetrics(
                        screenshotTimeMs = clock.elapsedRealtime() - startTimeMs,
                        screenshotAllocationByteCount = resultBitmap.allocationByteCount,
                        screenshotByteCount = resultBitmap.byteCount
                    )
                    val screenshotBytes = compressScreenshot(resultBitmap)
                    metrics.compressionTimeMs = clock.elapsedRealtime() - startTimeMs - metrics.screenshotTimeMs
                    metrics.compressionByteCount = screenshotBytes.size
                    logger.onScreenshotCaptured(screenshotBytes, metrics)
                } catch (e: Exception) {
                    finishOnError(
                        expected = false,
                        "Screenshot triggered: PixelCopy operation failed. Exception=${e.message}",
                        e
                    )
                } finally {
                    resultBitmap.recycle()
                }
            },
            mainThreadHandler.mainHandler,
        )
    }

    private fun finishOnError(expected: Boolean, message: String, e: Throwable? = null) {
        if (!expected) {
            errorHandler.handleError(message, e)
        }
        logger.logErrorInternal(message, e)
        Log.e("miguel-Screenshot", message)
        // Log empty screenshot on unblock the rust engine caller
        logger.onScreenshotCaptured(ByteArray(0), ScreenshotCaptureMetrics(0, 0, 0))
    }

    private fun compressScreenshot(resultBitmap: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { outStream ->
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 10, outStream)
            resultBitmap.recycle()
            // TODO(murki): Figure out if there's a more memory efficient way to do this
            //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
            //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
            //  and https://code.luasoftware.com/tutorials/android/android-convert-outputstream-to-inputstream
            outStream.toByteArray() // toByteArray will trigger copy, thus double memory usage
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