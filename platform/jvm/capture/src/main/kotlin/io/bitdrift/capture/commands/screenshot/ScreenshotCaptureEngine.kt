// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.phoneWindow
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
 * A capture either returns the JPEG or throws; [ScreenshotCaptureException] carries the reasons
 * the engine itself names, and the command reports either as its failure.
 *
 * @param mainThreadHandler hosts the one-line PixelCopy listener on API levels below 34.
 * @param windowManager locates the window to capture.
 * @param createBitmap allocates the destination for a root view; the engine owns and recycles it.
 */
internal class ScreenshotCaptureEngine(
    private val mainThreadHandler: MainThreadHandler,
    private val windowManager: IWindowManager,
    private val createBitmap: (View) -> Bitmap = ::rgb565BitmapFor,
) {
    /**
     * Captures a JPEG of the top-most visible window. Conditions the platform does not throw for
     * are thrown as [ScreenshotCaptureException]; anything the platform throws propagates as is,
     * for the command registry to report like any other handler failure.
     */
    suspend fun captureDeviceCommandScreenshot(): ByteArray {
        val rootView = windowManager.getTopMostVisibleRootView() ?: throw ScreenshotCaptureException("No visible root view")
        // TODO(murki): Figure out if we can scale down the bitmap surface area to reduce memory usage
        //  see: https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
        // TODO(murki): Use BuildVersionChecker after moving it to common module
        val bitmap =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> modernPixelCopy(rootView)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> legacyPixelCopy(rootView)
                // TODO(murki): Implement on old API levels using Canvas(bitmap) approach
                else -> throw ScreenshotCaptureException("Unsupported Android version=${Build.VERSION.SDK_INT}")
            }
        try {
            return compressScreenshot(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun modernPixelCopy(topView: View): Bitmap {
        // Our own RGB_565 destination: half the memory of the ARGB_8888 the platform would
        // otherwise allocate, and the same bitmap the legacy path uses.
        val bitmap = createBitmap(topView)
        val status =
            bitmap.orRecycle {
                // The whole window, stated explicitly: without a source rect the platform
                // substitutes the window's surface insets, the same region on a device but an
                // empty rect in tests.
                val request =
                    PixelCopy.Request.Builder
                        .ofWindow(topView)
                        .setSourceRect(Rect(0, 0, topView.width, topView.height))
                        .setDestinationBitmap(bitmap)
                        .build()
                suspendCancellableCoroutine { continuation ->
                    // A direct executor: the callback only resumes, so it needs no thread of its own.
                    PixelCopy.request(request, Runnable::run) { continuation.resume(it.status) }
                }
            }
        return bitmap.unlessRefused(status)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun legacyPixelCopy(root: View): Bitmap {
        val window = root.phoneWindow ?: throw ScreenshotCaptureException("Phone window invalid")
        val bitmap = createBitmap(root)
        val status =
            bitmap.orRecycle {
                suspendCancellableCoroutine { continuation ->
                    // On these API levels the copy itself runs synchronously here; only the status
                    // takes the detour through the handler.
                    PixelCopy.request(window, bitmap, { continuation.resume(it) }, mainThreadHandler.mainHandler)
                }
            }
        return bitmap.unlessRefused(status)
    }

    /** Runs [block], releasing this bitmap if it throws; the caller owns it only on success. */
    private inline fun <T> Bitmap.orRecycle(block: () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            recycle()
            throw e
        }

    private fun Bitmap.unlessRefused(status: Int): Bitmap {
        if (status != PixelCopy.SUCCESS) {
            recycle()
            throw ScreenshotCaptureException("PixelCopy failed. Result.status=${status.toStatusText()}")
        }
        return this
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
            PixelCopy.ERROR_UNKNOWN -> "UNKNOWN"
            PixelCopy.ERROR_TIMEOUT -> "TIMEOUT"
            PixelCopy.ERROR_SOURCE_NO_DATA -> "SOURCE_NO_DATA"
            PixelCopy.ERROR_SOURCE_INVALID -> "SOURCE_INVALID"
            PixelCopy.ERROR_DESTINATION_INVALID -> "DESTINATION_INVALID"
            else -> "Unknown error: $this"
        }
}

// The platform factory, so this module needs no core-ktx dependency.
@SuppressLint("UseKtx")
private fun rgb565BitmapFor(root: View): Bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.RGB_565)
