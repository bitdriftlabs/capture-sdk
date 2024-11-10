package io.bitdrift.capture.replay

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.PixelCopy
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.internal.DisplayManagers
import io.bitdrift.capture.replay.internal.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

internal class ScreenshotCaptureEngine(
    errorHandler: ErrorHandler,
    private val logger: IScreenshotLogger,
    context: Context,
    mainThreadHandler: MainThreadHandler,
    private val windowManager: WindowManager,
    displayManager: DisplayManagers,
    private val clock: IClock = DefaultClock.getInstance(),
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "io.bitdrift.capture.session-replay-screenshot")
    },
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
        val startTime = clock.elapsedRealtime()
        val topView = windowManager.findRootViews().lastOrNull() ?: return
        // TODO(murki): Consider calling setDestinationBitmap() with a Bitmap.Config.RGB_565 instead
        //  of the default of Bitmap.Config.ARGB_8888
        val screenshotRequest = PixelCopy.Request.Builder.ofWindow(topView).build()
        PixelCopy.request(screenshotRequest, executor) { screenshotResult ->
            if (screenshotResult.status != PixelCopy.SUCCESS) {
                // TODO(murki) Handle error
                Log.w("miguel-Screenshot", "PixelCopy operation failed. Result.status=${screenshotResult.status}")
                return@request
            }

            val screenshotTimeMs = clock.elapsedRealtime() - startTime
            val resultBitmap = screenshotResult.bitmap
            Log.d("miguel-Screenshot", "Miguel-PixelCopy finished capture on thread=${Thread.currentThread().name}, " +
                    "allocationByteCount=${resultBitmap.allocationByteCount}, " +
                    "byteCount=${resultBitmap.byteCount}, " +
                    "duration=$screenshotTimeMs")
            val stream = ByteArrayOutputStream()
            // TODO(murki): Confirm the exact compression method used on iOS
            // Encode bitmap to bytearray while compressing it using JPEG=60 quality
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            resultBitmap.recycle()
            // TODO (murki): Figure out if there's a more memory efficient way to do this
            //  see https://stackoverflow.com/questions/4989182/converting-java-bitmap-to-byte-array#comment36547795_4989543 and
            //  and https://gaumala.com/posts/2020-01-27-working-with-streams-kotlin.html
            val screenshotBytes = stream.toByteArray()
            val compressDurationMs = clock.elapsedRealtime() - startTime - screenshotTimeMs
            val totalDurationMs = compressDurationMs + screenshotTimeMs
            Log.d("miguel-Screenshot", "Miguel-Finished compression on thread=${Thread.currentThread().name}, " +
                    "screenshotBytes.size=${screenshotBytes.size}, " +
                    "duration=$compressDurationMs, " +
                    "totalDuration=$totalDurationMs")
            logger.onScreenshotCaptured(screenshotBytes, totalDurationMs)
        }

    }
}