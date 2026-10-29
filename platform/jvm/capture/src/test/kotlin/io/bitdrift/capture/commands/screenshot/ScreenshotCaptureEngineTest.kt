// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
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
 * only ever the delivery point of the legacy listener, and a capture that produces nothing throws:
 * the engine's own reasons as [ScreenshotCaptureException], the platform's exceptions untouched.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenshotCaptureEngineTest {
    @Test
    @Config(sdk = [24])
    fun unsupportedApiLevelIsAFailure(): Unit =
        runBlocking {
            val failure = engine(windowShowing(activityDecorView())).captureFailure()
            assertThat(failure.message).isEqualTo("Unsupported Android version=24")
        }

    @Test
    @Config(sdk = [33])
    fun noVisibleWindowIsAFailure(): Unit =
        runBlocking {
            val failure = engine(FakeWindowManager()).captureFailure()
            assertThat(failure.message).isEqualTo("No visible root view")
        }

    @Test
    @Config(sdk = [33])
    fun aRootViewWithoutAWindowIsAFailure(): Unit =
        runBlocking {
            val failure = engine(windowShowing(detachedView())).captureFailure()
            assertThat(failure.message).isEqualTo("Phone window invalid")
        }

    @Test
    @Config(sdk = [34])
    fun aPlatformRefusalPropagatesAsIs(): Unit =
        runBlocking {
            // The modern builder refuses a view that is not attached to a window; nothing wraps it.
            val thrown = runCatching { engine(windowShowing(detachedView())).captureDeviceCommandScreenshot() }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
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
            assertThat(mainLooper.isIdle).isTrue()
        }

    @Test
    @Config(sdk = [34])
    fun aSuccessfulCaptureRecyclesItsBitmap(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            assertThat(engine(windowShowing(activityDecorView()), createBitmap = bitmaps).captureDeviceCommandScreenshot()).isNotEmpty()
            assertThat(bitmaps.created.single().isRecycled).isTrue()
        }

    @Test
    @Config(sdk = [34])
    fun aRequestThatFailsToBuildRecyclesItsBitmap(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            val engine = engine(windowShowing(detachedView()), createBitmap = bitmaps)
            assertThat(
                runCatching { engine.captureDeviceCommandScreenshot() }.exceptionOrNull(),
            ).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(bitmaps.created.single().isRecycled).isTrue()
        }

    @Test
    @Config(sdk = [33])
    fun aRequestThatRefusesItsBitmapRecyclesIt(): Unit =
        runBlocking {
            // PixelCopy rejects an immutable destination before copying anything.
            val bitmaps = RecordingBitmaps(mutable = false)
            val engine = engine(windowShowing(activityDecorView()), createBitmap = bitmaps)
            assertThat(
                runCatching { engine.captureDeviceCommandScreenshot() }.exceptionOrNull(),
            ).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(bitmaps.created.single().isRecycled).isTrue()
        }

    private suspend fun ScreenshotCaptureEngine.captureFailure(): ScreenshotCaptureException =
        runCatching { captureDeviceCommandScreenshot() }.exceptionOrNull() as ScreenshotCaptureException

    private fun engine(
        windowManager: IWindowManager,
        listenerHandler: Handler = Handler(Looper.getMainLooper()),
        createBitmap: (View) -> Bitmap = RecordingBitmaps(),
    ): ScreenshotCaptureEngine {
        val mainThreadHandler: MainThreadHandler = mock { on { mainHandler } doReturn listenerHandler }
        return ScreenshotCaptureEngine(mainThreadHandler, windowManager, createBitmap)
    }

    /** Hands out RGB_565 bitmaps like the engine's default and remembers them, so a test can check they were recycled. */
    private class RecordingBitmaps(
        private val mutable: Boolean = true,
    ) : (View) -> Bitmap {
        val created = mutableListOf<Bitmap>()

        override fun invoke(root: View): Bitmap {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.RGB_565)
            return (if (mutable) bitmap else bitmap.copy(Bitmap.Config.RGB_565, false).also { bitmap.recycle() }).also { created += it }
        }
    }

    private fun activityDecorView(): View =
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .get()
            .window.decorView
            .apply { layout(0, 0, 1080, 2400) }

    /** A sized view that belongs to no window. */
    private fun detachedView(): View = View(Robolectric.buildActivity(Activity::class.java).setup().get()).apply { layout(0, 0, 10, 10) }

    private fun windowShowing(root: View): IWindowManager =
        object : IWindowManager {
            override fun getTopMostVisibleRootView(): View = root

            override fun getAllRootViews(): List<View> = listOf(root)

            override fun findFirstValidActivity(): Activity? = null
        }
}
