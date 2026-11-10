// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.phoneWindow
import io.bitdrift.capture.utils.BuildVersionChecker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Captures WebP screenshots of everything the app is showing through PixelCopy: the activity, plus
 * any dialog, sheet or popup over it, composited in the order the system stacks them in.
 *
 * Every step runs on the caller's thread, the command's own: the window lookup, the bitmaps, the
 * compression and the error reporting. PixelCopy's callbacks only resume the suspended capture.
 * Below Android 14 the platform insists on delivering its callback through a [android.os.Handler],
 * so that single resume is posted to [mainThreadHandler]; no work happens there.
 *
 * A capture either returns the WebP or throws; [ScreenshotCaptureException] carries the reasons
 * the engine itself names, and the command reports either as its failure. A window that cannot be
 * copied is left out of the screenshot, which fails only when no window could be captured.
 *
 * @param mainThreadHandler hosts the one-line PixelCopy listener on API levels below 34.
 * @param windowManager locates the windows to capture.
 * @param createBitmap allocates a bitmap of the given width, height and config; the engine owns and
 * recycles it.
 * @param buildVersionChecker selects the PixelCopy API the device supports.
 * @param copyTimeoutMillis how long a window's copy may take before the window is left out.
 */
internal class ScreenshotCaptureEngine(
    private val mainThreadHandler: MainThreadHandler,
    private val windowManager: IWindowManager,
    private val createBitmap: (width: Int, height: Int, config: Bitmap.Config) -> Bitmap = ::platformBitmap,
    private val buildVersionChecker: BuildVersionChecker = BuildVersionChecker(),
    private val copyTimeoutMillis: Long = COPY_TIMEOUT_MILLIS,
) {
    /**
     * Captures a WebP of all visible windows. Conditions the platform does not throw for are
     * thrown as [ScreenshotCaptureException]; anything the platform throws outside of a window's
     * copy propagates as is, for the command registry to report like any other handler failure.
     */
    suspend fun captureDeviceCommandScreenshot(): ByteArray {
        requireSupportedApiLevel()
        val windows = windowManager.visibleWindowsInStackingOrder()
        if (windows.isEmpty()) throw ScreenshotCaptureException(NO_VISIBLE_ROOT_VIEW)
        val screen = composite(windows)
        try {
            return compressScreenshot(screen)
        } finally {
            // Nothing writes to the screen bitmap but this thread, unlike a window's copy.
            screen.recycle()
        }
    }

    private fun requireSupportedApiLevel() {
        // TODO(murki): Implement on old API levels using Canvas(bitmap) approach
        if (!buildVersionChecker.isAtLeast(Build.VERSION_CODES.O)) {
            throw ScreenshotCaptureException("Unsupported Android version=${Build.VERSION.SDK_INT}")
        }
    }

    /**
     * Draws [windows], bottom to top, onto one bitmap that spans them all, black where no window is.
     * The caller owns the bitmap; it is recycled here if no window could be drawn.
     *
     * The screenshot is RGB_565: it is opaque, and a window's translucent pixels are blended as the
     * window is drawn onto it. The bottom window usually covers the whole screenshot, so it is copied
     * straight into it, and a screen with a single window takes no other bitmap.
     */
    private suspend fun composite(windows: List<ScreenWindow>): Bitmap {
        val bounds =
            Rect(
                windows.minOf { it.left },
                windows.minOf { it.top },
                windows.maxOf { it.left + it.width },
                windows.maxOf { it.top + it.height },
            )
        // TODO(murki): Figure out if we can scale down the bitmap surface area to reduce memory usage
        //  see: https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
        // A new RGB_565 bitmap is all black, so it needs no clearing.
        val newScreen = { createBitmap(bounds.width(), bounds.height(), Bitmap.Config.RGB_565) }
        var screen: Bitmap? = null
        try {
            var drawn = 0
            for (window in windows) {
                if (drawn == 0 && window.fills(bounds)) {
                    val target = screen ?: newScreen()
                    // The copy's until it reports, so that a failure below cannot recycle it under the copy.
                    screen = null
                    when (copyInto(window.root, target)) {
                        true -> {
                            screen = target
                            drawn++
                        }
                        false -> target.recycle()
                        // The copy may still be writing to it: left to the garbage collector.
                        null -> Unit
                    }
                } else {
                    val target = screen ?: newScreen().also { screen = it }
                    if (Canvas(target).drawWindow(window, bounds, overOtherWindows = drawn > 0)) drawn++
                }
            }
            if (drawn == 0) throw ScreenshotCaptureException(NO_VISIBLE_ROOT_VIEW)
            return checkNotNull(screen)
        } catch (e: Throwable) {
            screen?.recycle()
            throw e
        }
    }

    /**
     * Draws [window] onto this canvas at its place, over the windows already drawn when
     * [overOtherWindows]. Returns false, drawing nothing, when the window could not be copied.
     */
    private suspend fun Canvas.drawWindow(
        window: ScreenWindow,
        bounds: Rect,
        overOtherWindows: Boolean,
    ): Boolean {
        // Only a window with translucent pixels, such as a dialog's rounded corners, needs an alpha
        // channel, and only to blend them over the windows under it. Over the black of an empty
        // screenshot, the opaque copy's colours are already the blended ones.
        val config = if (overOtherWindows && !window.isOpaque) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val pixels = copyWindow(window.root, config) ?: return false
        val params = window.params
        // The dim behind a dialog is drawn by the system compositor, not by any window of the app.
        if (params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
            drawColor(Color.argb((params.dimAmount * MAX_ALPHA).roundToInt(), 0, 0, 0))
        }
        drawBitmap(pixels, (window.left - bounds.left).toFloat(), (window.top - bounds.top).toFloat(), null)
        pixels.recycle()
        return true
    }

    /**
     * Copies the last frame [root]'s window rendered into a new bitmap of [config], or returns null
     * if that did not work out.
     */
    private suspend fun copyWindow(
        root: View,
        config: Bitmap.Config,
    ): Bitmap? {
        val bitmap = createBitmap(root.width, root.height, config)
        val copied = copyInto(root, bitmap) ?: return null
        if (!copied) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /**
     * Copies the last frame [root]'s window rendered into [destination], which is the window's size.
     * Returns null when the copy may still be writing to [destination]: recycling a bitmap under a
     * copy aborts the process (https://github.com/getsentry/sentry-java/issues/5340), so it is left to
     * the garbage collector. Same when the command is cancelled mid-copy.
     */
    private suspend fun copyInto(
        root: View,
        destination: Bitmap,
    ): Boolean? = withTimeoutOrNull(copyTimeoutMillis) { copyPixels(root, destination) }

    /** Resumes with whether the platform copied [root]'s window into [destination]. */
    @Suppress("SwallowedException")
    private suspend fun copyPixels(
        root: View,
        destination: Bitmap,
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            val onResult = { status: Int -> continuation.resume(status == PixelCopy.SUCCESS) }
            try {
                when {
                    buildVersionChecker.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) -> modernPixelCopy(root, destination, onResult)
                    else -> legacyPixelCopy(root, destination, onResult)
                }
            } catch (e: RuntimeException) {
                // Usually an IllegalArgumentException: the window lost its surface, it is going away.
                if (continuation.isActive) continuation.resume(false)
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun modernPixelCopy(
        root: View,
        destination: Bitmap,
        onResult: (Int) -> Unit,
    ) {
        // The whole window, stated explicitly: without a source rect the platform copies the whole
        // surface, shadow margins included, which a source rect from the root view's origin would
        // then be offset by.
        val request =
            PixelCopy.Request.Builder
                .ofWindow(root)
                .setSourceRect(Rect(0, 0, root.width, root.height))
                .setDestinationBitmap(destination)
                .build()
        // A direct executor: the callback only resumes, so it needs no thread of its own.
        PixelCopy.request(request, Runnable::run) { onResult(it.status) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun legacyPixelCopy(
        root: View,
        destination: Bitmap,
        onResult: (Int) -> Unit,
    ) {
        // On these API levels the copy itself runs synchronously here; only the status takes the
        // detour through the handler.
        val handler = mainThreadHandler.mainHandler
        val window = root.phoneWindow
        if (window != null) {
            PixelCopy.request(window, destination, onResult, handler)
            return
        }
        // Popups have no Window below API 34: copy from their surface, like PixelCopy.request(Window) does.
        val surface = root.windowSurface()
        if (surface == null) {
            onResult(PixelCopy.ERROR_SOURCE_INVALID)
        } else {
            PixelCopy.request(surface, root.surfaceContentRect(), destination, onResult, handler)
        }
    }

    private fun compressScreenshot(resultBitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { outStream ->
            // TODO(murki): Consider also resizing the bitmap screenshot to a smaller size
            resultBitmap.compress(lossyWebp(), WEBP_QUALITY, outStream)
            // TODO(murki): Figure out if there's a more memory efficient way to do this
            //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
            //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
            //  and https://code.luasoftware.com/tutorials/android/android-convert-outputstream-to-inputstream
            outStream.toByteArray()
        }

    internal companion object {
        /** WebP quality: flat colours and sharp text stay legible while the file stays small. */
        const val WEBP_QUALITY = 80

        /**
         * A copy that never calls back leaves its window out, well within the time the core gives the
         * whole command. Copies take a few milliseconds.
         */
        const val COPY_TIMEOUT_MILLIS = 1_000L

        const val NO_VISIBLE_ROOT_VIEW = "No visible root view"

        private const val MAX_ALPHA = 255
    }
}

/**
 * [Bitmap.CompressFormat.WEBP_LOSSY] only exists from API 30. Below that the deprecated
 * [Bitmap.CompressFormat.WEBP] is the same lossy encoder for any quality under 100.
 */
@Suppress("DEPRECATION")
private fun lossyWebp(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

// The platform factory, so this module needs no core-ktx dependency.
@SuppressLint("UseKtx")
private fun platformBitmap(
    width: Int,
    height: Int,
    config: Bitmap.Config,
): Bitmap = Bitmap.createBitmap(width, height, config)
