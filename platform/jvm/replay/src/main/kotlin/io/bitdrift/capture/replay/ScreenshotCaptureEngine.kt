package io.bitdrift.capture.replay

import android.content.Context
import android.util.Log
import io.bitdrift.capture.common.DefaultClock
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IClock
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.internal.DisplayManagers
import io.bitdrift.capture.replay.internal.WindowManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class ScreenshotCaptureEngine internal constructor(
    errorHandler: ErrorHandler,
    logger: IReplayLogger,
    context: Context,
    mainThreadHandler: MainThreadHandler,
    windowManager: WindowManager,
    displayManager: DisplayManagers,
    clock: IClock = DefaultClock.getInstance(),
    executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "io.bitdrift.capture.session-replay-screenshot")
    },
) {

    init {
        // Log to console all dependency addresses
        Log.d("Miguel", "$errorHandler, $logger, $context, $mainThreadHandler, $windowManager, $displayManager, $clock, $executor")
    }

    fun captureScreenshot() {
        //logger.onScreenCaptured()
    }
}