// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.replay.internal

import android.graphics.Bitmap
import android.os.Build
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.phoneWindow
import io.bitdrift.capture.replay.IScreenshotLogger
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

internal class ScreenshotCaptureEngine(
    private val errorHandler: ErrorHandler,
    private val logger: IScreenshotLogger,
    private val mainThreadHandler: MainThreadHandler,
    private val windowManager: IWindowManager,
    private val executor: ExecutorService,
    private val metrics: ScreenshotMetricsStopwatch = ScreenshotMetricsStopwatch(),
) {
    fun captureScreenshot() {
        try {
            metrics.start()
            val rootView = windowManager.getFirstRootView()
            if (rootView == null || rootView.width <= 0 || rootView.height <= 0 || !rootView.isShown) {
                finishOnError(expected = true, "Screenshot triggered: Root view is invalid, skipping capture")
                return
            }

            // TODO(murki): Figure out if we can scale down the bitmap surface area to reduce memory usage
            //  see: https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
            // TODO(murki): Use BuildVersionChecker after moving it to common module
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                modernPixelCopySnapshot(rootView)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopySnapshot(rootView)
            } else {
                // TODO(murki): Implement on old API levels using Canvas(bitmap) approach
                finishOnError(
                    expected = true,
                    "Screenshot triggered: Unsupported Android version=${Build.VERSION.SDK_INT}, skipping capture",
                )
            }
        } catch (e: Exception) {
            finishOnError(
                expected = false,
                "Screenshot triggered: PixelCopy request failed. Exception=${e.message}",
                e,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun modernPixelCopySnapshot(topView: View) {
        // TODO(murki): Reduce memory footprint by calling setDestinationBitmap() with a Bitmap.Config.RGB_565 instead
        //  of the default of Bitmap.Config.ARGB_8888
        val screenshotRequest =
            PixelCopy.Request.Builder
                .ofWindow(topView)
                .build()
        PixelCopy.request(screenshotRequest, executor) { screenshotResult ->
            var resultBitmap: Bitmap? = null
            try {
                if (screenshotResult.status != PixelCopy.SUCCESS) {
                    finishOnError(
                        expected = false,
                        "Screenshot triggered: PixelCopy operation failed. Result.status=${screenshotResult.status}",
                    )
                    return@request
                }
                // bitmap is only available if the status is SUCCESS
                resultBitmap = screenshotResult.bitmap
                metrics.screenshot(resultBitmap.allocationByteCount, resultBitmap.byteCount)
                val screenshotBytes = compressScreenshot(resultBitmap)
                metrics.compression(screenshotBytes.size)
                logger.onScreenshotCaptured(screenshotBytes, metrics.data())
            } catch (e: Exception) {
                finishOnError(
                    expected = false,
                    "Screenshot triggered: PixelCopy compression failed. Exception=${e.message}",
                    e,
                )
            } finally {
                resultBitmap?.recycle()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pixelCopySnapshot(root: View) {
        val window = root.phoneWindow
        if (window == null) {
            finishOnError(expected = true, "Screenshot triggered: Phone window invalid, skipping capture")
            return
        }

        val resultBitmap =
            createBitmap(root.width, root.height, Bitmap.Config.RGB_565)
        // TODO(murki): Figure out if there's any benefit from calling this using mainThreadHandler.mainHandler.post{ }
        PixelCopy.request(
            window,
            resultBitmap,
            { screenshotResultStatus: Int ->
                if (screenshotResultStatus != PixelCopy.SUCCESS) {
                    resultBitmap.recycle()
                    finishOnError(
                        expected = false,
                        "Screenshot triggered: PixelCopy operation failed. Result.status=${screenshotResultStatus.toStatusText()}",
                    )
                    return@request
                }

                // TODO(murki): Try to avoid so much context switching between main and background threads
                executor.execute {
                    try {
                        metrics.screenshot(resultBitmap.allocationByteCount, resultBitmap.byteCount)
                        val screenshotBytes = compressScreenshot(resultBitmap)
                        metrics.compression(screenshotBytes.size)
                        logger.onScreenshotCaptured(screenshotBytes, metrics.data())
                    } catch (e: Exception) {
                        finishOnError(
                            expected = false,
                            "Screenshot triggered: Compression operation failed. Exception=${e.message}",
                            e,
                        )
                    } finally {
                        resultBitmap.recycle()
                    }
                }
            },
            mainThreadHandler.mainHandler,
        )
    }

    private fun finishOnError(
        expected: Boolean,
        message: String,
        e: Throwable? = null,
    ) {
        if (!expected) {
            errorHandler.handleError(message, e)
        }
        logger.logErrorInternal(message, e)
        // Log empty screenshot on unblock the rust engine caller
        logger.onScreenshotCaptured(ByteArray(0), metrics.data())
    }

    private fun compressScreenshot(resultBitmap: Bitmap): ByteArray {
        // TODO(murki): Consider also resizing the bitmap screenshot to a smaller size
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

    private fun Int.toStatusText(): String =
        when (this) {
            PixelCopy.SUCCESS -> "SUCCESS"
            PixelCopy.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            PixelCopy.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
            PixelCopy.ERROR_SOURCE_NO_DATA -> "ERROR_SOURCE_NO_DATA"
            PixelCopy.ERROR_SOURCE_INVALID -> "ERROR_SOURCE_INVALID"
            PixelCopy.ERROR_DESTINATION_INVALID -> "ERROR_DESTINATION_INVALID"
            else -> "Unknown error: $this"
        }
}
