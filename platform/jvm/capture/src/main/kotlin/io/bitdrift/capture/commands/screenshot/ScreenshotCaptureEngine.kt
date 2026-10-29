// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// phoneWindow is internal to the common module; this is how the replay module reached it too.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.bitdrift.capture.commands.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.phoneWindow
import io.bitdrift.capture.providers.ArrayFields
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Captures JPEG screenshots of the app's window through PixelCopy.
 *
 * Every step runs on the caller's thread, the command's own: the window lookup, the bitmap, the
 * compression and the error reporting. PixelCopy's callbacks only resume the suspended capture.
 * Below Android 14 the platform insists on delivering its callback through a [android.os.Handler],
 * so that single resume is posted to [mainThreadHandler]; no work happens there.
 *
 * @param errorHandler reports unexpected capture failures.
 * @param mainThreadHandler hosts the one-line PixelCopy listener on API levels below 34.
 * @param windowManager locates the window to capture.
 * @param logger records capture-failure diagnostics in the session.
 */
internal class ScreenshotCaptureEngine(
    private val errorHandler: ErrorHandler,
    private val mainThreadHandler: MainThreadHandler,
    private val windowManager: IWindowManager,
    private val logger: IInternalLogger,
) {
    /** Captures a JPEG of the top-most visible window; empty bytes mean the capture failed. */
    suspend fun captureDeviceCommandScreenshot(): ByteArray {
        val rootView =
            windowManager.getTopMostVisibleRootView()
                ?: return failure(expected = true, "Device command screenshot: No visible root view")
        val bitmap =
            try {
                // TODO(murki): Figure out if we can scale down the bitmap surface area to reduce memory usage
                //  see: https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
                // TODO(murki): Use BuildVersionChecker after moving it to common module
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> modernPixelCopy(rootView)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> legacyPixelCopy(rootView)
                    // TODO(murki): Implement on old API levels using Canvas(bitmap) approach
                    else -> return failure(
                        expected = true,
                        "Device command screenshot: Unsupported Android version=${Build.VERSION.SDK_INT}",
                    )
                }
            } catch (e: Exception) {
                return failure(expected = false, "Device command screenshot: PixelCopy request failed. Exception=${e.message}", e)
            }
        return try {
            compressScreenshot(bitmap)
        } catch (e: Exception) {
            failure(expected = false, "Device command screenshot: Compression failed. Exception=${e.message}", e)
        } finally {
            bitmap.recycle()
        }
    }

    /** Thrown when PixelCopy reports a status other than success. */
    private class PixelCopyRefusedException(
        status: String,
    ) : Exception("PixelCopy failed. Result.status=$status")

    @SuppressLint("UseKtx")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun modernPixelCopy(topView: View): Bitmap {
        // Our own RGB_565 destination: half the memory of the ARGB_8888 the platform would
        // otherwise allocate, and the same bitmap the legacy path uses.
        val bitmap = Bitmap.createBitmap(topView.width, topView.height, Bitmap.Config.RGB_565)
        // The whole window, stated explicitly: without a source rect the platform substitutes the
        // window's surface insets, which is the same region on a device but an empty rect in tests.
        val request =
            PixelCopy.Request.Builder
                .ofWindow(topView)
                .setSourceRect(Rect(0, 0, topView.width, topView.height))
                .setDestinationBitmap(bitmap)
                .build()
        val status =
            suspendCancellableCoroutine { continuation ->
                // A direct executor: the callback only resumes, so it needs no thread of its own.
                PixelCopy.request(request, Runnable::run) { continuation.resume(it.status) }
            }
        if (status != PixelCopy.SUCCESS) {
            bitmap.recycle()
            throw PixelCopyRefusedException(status.toString())
        }
        return bitmap
    }

    @SuppressLint("UseKtx")
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun legacyPixelCopy(root: View): Bitmap {
        val window = root.phoneWindow ?: throw IllegalStateException("Phone window invalid")
        // The platform factory, so this module needs no core-ktx dependency.
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.RGB_565)
        val status =
            suspendCancellableCoroutine { continuation ->
                // On these API levels the copy itself runs synchronously here; only the status
                // takes the detour through the handler.
                PixelCopy.request(window, bitmap, { continuation.resume(it) }, mainThreadHandler.mainHandler)
            }
        if (status != PixelCopy.SUCCESS) {
            bitmap.recycle()
            throw PixelCopyRefusedException(status.toStatusText())
        }
        return bitmap
    }

    private fun failure(
        expected: Boolean,
        message: String,
        e: Throwable? = null,
    ): ByteArray {
        if (!expected) {
            errorHandler.handleError(message, e)
        }
        logger.logInternal(LogType.INTERNALSDK, LogLevel.ERROR, arrayFields = ArrayFields.EMPTY, throwable = e) { message }
        return ByteArray(0)
    }

    private fun compressScreenshot(resultBitmap: Bitmap): ByteArray {
        // TODO(murki): Consider also resizing the bitmap screenshot to a smaller size
        return ByteArrayOutputStream().use { outStream ->
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 10, outStream)
            // TODO(murki): Figure out if there's a more memory efficient way to do this
            //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
            //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
            //  and https://code.luasoftware.com/tutorials/android/android-convert-outputstream-to-inputstream
            outStream.toByteArray()
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
