// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.fakes.FakeWindowManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Exercises [ScreenshotCaptureEngine] against Robolectric's PixelCopy on both API paths. The
 * contract under test: the capture completes on the caller's coroutine, the injected handler is
 * only ever the delivery point of the legacy listener, and failures come back as empty bytes with
 * the expected ones logged but not reported as SDK errors.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenshotCaptureEngineTest {
    private val errorHandler = RecordingErrorHandler()
    private val logger: IInternalLogger = mock()

    @Test
    @Config(sdk = [24])
    fun unsupportedApiLevelIsAnExpectedFailure(): Unit =
        runBlocking {
            val engine = engine(windowShowing(activityDecorView()))
            assertThat(engine.captureDeviceCommandScreenshot()).isEmpty()
            assertThat(errorHandler.errors).isEmpty()
        }

    @Test
    @Config(sdk = [33])
    fun noVisibleWindowIsAnExpectedFailure(): Unit =
        runBlocking {
            val engine = engine(FakeWindowManager())
            assertThat(engine.captureDeviceCommandScreenshot()).isEmpty()
            assertThat(errorHandler.errors).isEmpty()
        }

    @Test
    @Config(sdk = [33])
    fun legacyPathOnlyUsesTheHandlerToDeliverTheListener(): Unit =
        runBlocking {
            // A looper of its own, so the delivery is observable: Robolectric's PixelCopy calls a
            // main-looper listener inline, but posts to any other handler like the platform does.
            val listenerThread = HandlerThread("pixelcopy-listener").apply { start() }
            val engine = engine(windowShowing(activityDecorView()), Handler(listenerThread.looper))
            val mainLooper = shadowOf(Looper.getMainLooper()).apply { idle() }
            val capture = async(start = CoroutineStart.UNDISPATCHED) { engine.captureDeviceCommandScreenshot() }

            // The copy already ran on this thread; the capture waits only for the status, which
            // arrives once the listener's looper runs, and then resumes on this coroutine.
            assertThat(capture.isCompleted).isFalse()
            shadowOf(listenerThread.looper).idle()

            assertThat(capture.await()).isNotEmpty()
            assertThat(mainLooper.isIdle).isTrue()
            assertThat(errorHandler.errors).isEmpty()
            listenerThread.quit()
        }

    @Test
    @Config(sdk = [34])
    fun modernPathCompletesWithoutAnyLooper(): Unit =
        runBlocking {
            val engine = engine(windowShowing(activityDecorView()))
            val mainLooper = shadowOf(Looper.getMainLooper()).apply { idle() }
            val capture = async(start = CoroutineStart.UNDISPATCHED) { engine.captureDeviceCommandScreenshot() }

            // The callback runs on a direct executor and only resumes, so awaiting the capture
            // needs no looper to be driven: nothing was posted anywhere.
            assertThat(capture.await()).isNotEmpty()
            assertThat(errorHandler.errors).isEmpty()
            assertThat(mainLooper.isIdle).isTrue()
        }

    @Test
    @Config(sdk = [33])
    fun aRootViewWithoutAWindowIsReportedAsAnSdkError(): Unit =
        runBlocking {
            val detached = View(Robolectric.buildActivity(Activity::class.java).setup().get()).apply { layout(0, 0, 10, 10) }
            val engine = engine(windowShowing(detached))
            assertThat(engine.captureDeviceCommandScreenshot()).isEmpty()
            assertThat(errorHandler.errors).hasSize(1)
            assertThat(errorHandler.errors.single().first).contains("PixelCopy request failed")
        }

    private fun engine(
        windowManager: IWindowManager,
        listenerHandler: Handler = Handler(Looper.getMainLooper()),
    ): ScreenshotCaptureEngine {
        val mainThreadHandler: MainThreadHandler = mock { on { mainHandler } doReturn listenerHandler }
        return ScreenshotCaptureEngine(errorHandler, mainThreadHandler, windowManager, logger)
    }

    private fun activityDecorView(): View =
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .get()
            .window.decorView
            .apply { layout(0, 0, 1080, 2400) }

    private fun windowShowing(root: View): IWindowManager =
        object : IWindowManager {
            override fun getTopMostVisibleRootView(): View = root

            override fun getAllRootViews(): List<View> = listOf(root)

            override fun findFirstValidActivity(): Activity? = null
        }

    /** Records SDK error reports so a failing assertion can say what the engine reported. */
    private class RecordingErrorHandler : ErrorHandler {
        val errors = mutableListOf<Pair<String, Throwable?>>()

        override fun handleError(
            detail: String,
            e: Throwable?,
        ) {
            errors += detail to e
        }
    }
}
