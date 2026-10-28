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
import androidx.annotation.RestrictTo
import androidx.core.graphics.createBitmap
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.phoneWindow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

/**
 * Captures JPEG screenshots for SDK commands independently of session replay.
 *
 * @param errorHandler reports unexpected capture failures.
 * @param mainThreadHandler receives legacy PixelCopy callbacks.
 * @param windowManager locates the window to capture.
 * @param executor runs screenshot processing.
 * @param logError records capture-failure diagnostics.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ScreenshotCaptureEngine(
    private val errorHandler: ErrorHandler,
    private val mainThreadHandler: MainThreadHandler,
    private val windowManager: IWindowManager,
    private val executor: ExecutorService,
    private val logError: (String, Throwable?) -> Unit,
) {
    /** Captures a JPEG, delivering empty bytes to [completion] if capture fails. */
    fun captureDeviceCommandScreenshot(completion: (ByteArray) -> Unit) {
        try {
            val rootView = windowManager.getBottomMostRootView()
            if (rootView == null || rootView.width <= 0 || rootView.height <= 0 || !rootView.isShown) {
                finishOnError(expected = true, "Device command screenshot: Root view is invalid", completion = completion)
                return
            }

            // TODO(murki): Figure out if we can scale down the bitmap surface area to reduce memory usage
            //  see: https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
            // TODO(murki): Use BuildVersionChecker after moving it to common module
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                modernPixelCopySnapshot(rootView, completion)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopySnapshot(rootView, completion)
            } else {
                // TODO(murki): Implement on old API levels using Canvas(bitmap) approach
                finishOnError(
                    expected = true,
                    "Device command screenshot: Unsupported Android version=${Build.VERSION.SDK_INT}",
                    completion = completion,
                )
            }
        } catch (e: Exception) {
            finishOnError(
                expected = false,
                "Device command screenshot: PixelCopy request failed. Exception=${e.message}",
                e,
                completion,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun modernPixelCopySnapshot(
        topView: View,
        completion: (ByteArray) -> Unit,
    ) {
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
                        "Device command screenshot: PixelCopy failed. Result.status=${screenshotResult.status}",
                        completion = completion,
                    )
                    return@request
                }
                // bitmap is only available if the status is SUCCESS
                resultBitmap = screenshotResult.bitmap
                val screenshotBytes = compressScreenshot(resultBitmap)
                completion(screenshotBytes)
            } catch (e: Exception) {
                finishOnError(
                    expected = false,
                    "Device command screenshot: PixelCopy compression failed. Exception=${e.message}",
                    e,
                    completion,
                )
            } finally {
                resultBitmap?.recycle()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pixelCopySnapshot(
        root: View,
        completion: (ByteArray) -> Unit,
    ) {
        val window = root.phoneWindow
        if (window == null) {
            finishOnError(expected = true, "Device command screenshot: Phone window invalid", completion = completion)
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
                        "Device command screenshot: PixelCopy failed. Result.status=${screenshotResultStatus.toStatusText()}",
                        completion = completion,
                    )
                    return@request
                }

                // TODO(murki): Try to avoid so much context switching between main and background threads
                executor.execute {
                    try {
                        val screenshotBytes = compressScreenshot(resultBitmap)
                        completion(screenshotBytes)
                    } catch (e: Exception) {
                        finishOnError(
                            expected = false,
                            "Device command screenshot: Compression failed. Exception=${e.message}",
                            e,
                            completion,
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
        completion: (ByteArray) -> Unit,
    ) {
        if (!expected) {
            errorHandler.handleError(message, e)
        }
        logError(message, e)
        completion(ByteArray(0))
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
