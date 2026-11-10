// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Exercises [ScreenshotCaptureEngine] against Robolectric's PixelCopy on both API paths. The
 * contract under test: the capture completes on the caller's coroutine, the injected handler is
 * only ever the delivery point of the legacy listener, every visible window ends up in one WebP in
 * the order the system stacks them in, and a capture that produces nothing throws: the engine's own
 * reasons as [ScreenshotCaptureException], the platform's exceptions untouched.
 *
 * Native graphics, so that the screenshots really are encoded and can be decoded again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotCaptureEngineTest {
    private val windows by lazy { ScreenshotTestWindows() }

    @Test
    @Config(sdk = [24])
    fun unsupportedApiLevelIsAFailure(): Unit =
        runBlocking {
            val failure = engine(windows.showing(windows.activityWindow())).captureFailure()
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
    @Config(sdk = [34])
    fun aRootViewThatIsNotAttachedToAWindowIsNotCaptured(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            val failure = engine(FakeWindowManager(rootViews = listOf(detachedView())), createBitmap = bitmaps).captureFailure()
            assertThat(failure.message).isEqualTo("No visible root view")
            assertThat(bitmaps.created).isEmpty()
        }

    @Test
    @Config(sdk = [34])
    fun aWindowThatIsNotVisibleIsNotCaptured(): Unit =
        runBlocking {
            val activity = windows.activityWindow()
            val hiddenDialog = windows.dialogWindow()
            val windowManager = windows.showing(activity, hiddenDialog)
            windows.hide(hiddenDialog)
            val screenshot = engine(windowManager).captureDecoded()
            // Only the activity: no dim and no blue.
            assertColor(screenshot.getPixel(100, 200), Color.RED)
        }

    @Test
    @Config(sdk = [33])
    fun legacyPathOnlyUsesTheHandlerToDeliverTheListener(): Unit =
        runBlocking {
            // A looper of its own, so the delivery is observable: Robolectric's PixelCopy calls a
            // main-looper listener inline, but posts to any other handler like the platform does.
            // Paused, so the post stays queued until the test runs it rather than racing this thread.
            val listenerThread = HandlerThread("pixelcopy-listener").apply { start() }
            shadowOf(listenerThread.looper).pause()
            val engine = engine(windows.showing(windows.activityWindow()), Handler(listenerThread.looper))
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
            val engine = engine(windows.showing(windows.activityWindow()))
            val mainLooper = shadowOf(Looper.getMainLooper()).apply { idle() }
            val capture = async(start = CoroutineStart.UNDISPATCHED) { engine.captureDeviceCommandScreenshot() }

            // The callback runs on a direct executor and only resumes, so awaiting the capture
            // needs no looper to be driven: nothing was posted anywhere.
            assertThat(capture.await()).isNotEmpty()
            assertThat(mainLooper.isIdle).isTrue()
        }

    @Test
    @Config(sdk = [29])
    fun theScreenshotIsALossyWebPBelowApi30() = assertTheScreenshotIsAWebP()

    @Test
    @Config(sdk = [34])
    fun theScreenshotIsALossyWebP() = assertTheScreenshotIsAWebP()

    private fun assertTheScreenshotIsAWebP(): Unit =
        runBlocking {
            val bytes = engine(windows.showing(windows.activityWindow())).captureDeviceCommandScreenshot()
            // A RIFF container whose form type is WEBP.
            assertThat(String(bytes, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
            assertThat(String(bytes, 8, 4, Charsets.US_ASCII)).isEqualTo("WEBP")
        }

    @Test
    @Config(sdk = [33])
    fun aDialogOverAnActivityIsCapturedWithItsDimOnApi33() = assertADialogIsCapturedOverItsActivity()

    @Test
    @Config(sdk = [34])
    fun aDialogOverAnActivityIsCapturedWithItsDim() = assertADialogIsCapturedOverItsActivity()

    private fun assertADialogIsCapturedOverItsActivity(): Unit =
        runBlocking {
            val activity = windows.activityWindow(Color.RED)
            val dialog = windows.dialogWindow(Color.BLUE, left = 50, top = 100, right = 150, bottom = 300, dimAmount = 0.5f)
            val screenshot = engine(windows.showing(activity, dialog)).captureDecoded()

            // One image the size of the activity, with the dialog in it.
            assertThat(screenshot.width).isEqualTo(200)
            assertThat(screenshot.height).isEqualTo(400)
            assertColor(screenshot.getPixel(100, 200), Color.BLUE)
            // The activity, dimmed behind the dialog by the system's compositor: half way to black.
            assertColor(screenshot.getPixel(20, 20), Color.rgb(127, 0, 0))
        }

    @Test
    @Config(sdk = [34])
    fun anOpaqueDialogIsCopiedWithoutAnAlphaChannel(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            val screenshot =
                engine(windows.showing(windows.activityWindow(Color.RED), windows.dialogWindow(Color.BLUE)), createBitmap = bitmaps)
                    .captureDecoded()
            assertColor(screenshot.getPixel(100, 200), Color.BLUE)
            // The screenshot, which the activity was copied straight into, and the dialog's copy.
            assertThat(bitmaps.created.map { it.config }).containsExactly(Bitmap.Config.RGB_565, Bitmap.Config.RGB_565)
        }

    @Test
    @Config(sdk = [33])
    fun aTranslucentDialogIsBlendedOverTheActivityOnApi33() = assertATranslucentDialogIsBlendedOverTheActivity()

    @Test
    @Config(sdk = [34])
    fun aTranslucentDialogIsBlendedOverTheActivity() = assertATranslucentDialogIsBlendedOverTheActivity()

    private fun assertATranslucentDialogIsBlendedOverTheActivity(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            val activity = windows.activityWindow(Color.RED)
            val dialog = windows.dialogWindow(Color.argb(128, 0, 0, 255), dimAmount = 0f)
            val screenshot = engine(windows.showing(activity, dialog), createBitmap = bitmaps).captureDecoded()

            // Half the activity's red, half the dialog's blue.
            assertColor(screenshot.getPixel(100, 200), Color.rgb(127, 0, 128))
            // Only the dialog's copy has an alpha channel.
            assertThat(bitmaps.created.map { it.config }).containsExactly(Bitmap.Config.RGB_565, Bitmap.Config.ARGB_8888)
        }

    @Test
    @Config(sdk = [34])
    fun aDialogWithoutADimLeavesTheActivityAsItIs(): Unit =
        runBlocking {
            val screenshot =
                engine(windows.showing(windows.activityWindow(Color.RED), windows.dialogWindow(dimAmount = 0f))).captureDecoded()
            assertColor(screenshot.getPixel(20, 20), Color.RED)
        }

    @Test
    @Config(sdk = [34])
    fun aSecureWindowIsBlackAndNeverCopied(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            val activity = windows.activityWindow(Color.RED)
            val secureDialog = windows.dialogWindow(Color.BLUE, secure = true)
            val screenshot = engine(windows.showing(activity, secureDialog), createBitmap = bitmaps).captureDecoded()

            assertColor(screenshot.getPixel(100, 200), Color.BLACK)
            assertColor(screenshot.getPixel(20, 20), Color.rgb(127, 0, 0))
            // Only the screenshot, which the activity was copied straight into: nothing for the secure window.
            assertThat(bitmaps.created).hasSize(1)
        }

    @Test
    @Config(sdk = [34])
    fun aScreenWithOnlyASecureWindowIsAllBlack(): Unit =
        runBlocking {
            val secureDialog = windows.dialogWindow(Color.BLUE, left = 0, top = 0, right = 200, bottom = 400, secure = true)
            val screenshot = engine(windows.showing(secureDialog)).captureDecoded()
            assertColor(screenshot.getPixel(100, 200), Color.BLACK)
        }

    @Test
    @Config(sdk = [33])
    fun aPopupIsStackedAboveItsParentAndUnderLaterDialogsOnApi33() = assertAPopupIsStackedRightAboveItsParent()

    @Test
    @Config(sdk = [34])
    fun aPopupIsStackedAboveItsParentAndUnderLaterDialogs() = assertAPopupIsStackedRightAboveItsParent()

    private fun assertAPopupIsStackedRightAboveItsParent(): Unit =
        runBlocking {
            val activity = windows.activityWindow(Color.RED)
            // Added after the dialog, so last in the window list, but attached to the activity.
            val dialog = windows.dialogWindow(Color.BLUE, left = 60, top = 100, right = 180, bottom = 300, dimAmount = 0f)
            val popup = windows.popupWindow(activity, Color.GREEN, left = 20, top = 200, right = 120, bottom = 350)
            val screenshot = engine(windows.showing(activity, dialog, popup)).captureDecoded()

            // The popup covers the activity...
            assertColor(screenshot.getPixel(40, 330), Color.GREEN)
            // ...but not the dialog, which was added after the activity.
            assertColor(screenshot.getPixel(100, 250), Color.BLUE)
            assertColor(screenshot.getPixel(150, 150), Color.BLUE)
        }

    @Test
    @Config(sdk = [34])
    fun aPopupWhoseParentIsNotShownIsStackedOnTop(): Unit =
        runBlocking {
            val activity = windows.activityWindow(Color.RED)
            val dialog = windows.dialogWindow(Color.BLUE, left = 60, top = 100, right = 180, bottom = 300, dimAmount = 0f)
            val orphan = windows.popupWindow(activity, Color.GREEN, left = 20, top = 200, right = 120, bottom = 350, token = Binder())
            val screenshot = engine(windows.showing(orphan, activity, dialog)).captureDecoded()
            assertColor(screenshot.getPixel(100, 250), Color.GREEN)
        }

    @Test
    @Config(sdk = [33])
    fun aPhoneSizedScreenIsCapturedAtItsFullSize(): Unit =
        runBlocking {
            val phone = ScreenshotTestWindows(width = 1080, height = 2400)
            val bitmaps = RecordingBitmaps()
            val screenshot = engine(phone.showing(phone.activityWindow(Color.RED)), createBitmap = bitmaps).captureDecoded()
            assertThat(screenshot.width).isEqualTo(1080)
            assertThat(screenshot.height).isEqualTo(2400)
            assertColor(screenshot.getPixel(540, 1200), Color.RED)
            // Only the screenshot, which the activity was copied straight into.
            assertThat(bitmaps.created.map { Triple(it.width, it.height, it.config) })
                .containsExactly(Triple(1080, 2400, Bitmap.Config.RGB_565))
        }

    @Test
    @Config(sdk = [34])
    fun aSuccessfulCaptureRecyclesItsBitmaps(): Unit =
        runBlocking {
            val bitmaps = RecordingBitmaps()
            val activity = windows.activityWindow()
            val dialog = windows.dialogWindow()
            assertThat(engine(windows.showing(activity, dialog), createBitmap = bitmaps).captureDeviceCommandScreenshot()).isNotEmpty()
            // The screenshot, which the activity was copied straight into, and the dialog's copy.
            assertThat(bitmaps.created).hasSize(2)
            assertThat(bitmaps.created.map { it.isRecycled }).containsOnly(true)
        }

    @Test
    @Config(sdk = [34])
    fun aWindowWhoseCopyFailsIsLeftOut(): Unit =
        runBlocking {
            // PixelCopy rejects an immutable destination before copying anything; here it is the dialog's.
            val bitmaps = RecordingBitmaps(immutableFrom = 1)
            val activity = windows.activityWindow(Color.RED)
            val dialog = windows.dialogWindow(Color.BLUE, dimAmount = 0f)
            val screenshot = engine(windows.showing(activity, dialog), createBitmap = bitmaps).captureDecoded()

            assertColor(screenshot.getPixel(100, 200), Color.RED)
            assertThat(bitmaps.created.map { it.isRecycled }).containsOnly(true)
        }

    @Test
    @Config(sdk = [33])
    fun aCaptureWhereNoCopyWorksRecyclesItsBitmapsAndFails(): Unit =
        runBlocking {
            // The activity is copied straight into the screenshot's bitmap, here an immutable one.
            val bitmaps = RecordingBitmaps(immutableFrom = 0)
            val failure = engine(windows.showing(windows.activityWindow()), createBitmap = bitmaps).captureFailure()
            assertThat(failure.message).isEqualTo("No visible root view")
            assertThat(bitmaps.created).hasSize(1)
            assertThat(bitmaps.created.map { it.isRecycled }).containsOnly(true)
        }

    @Test
    @Config(sdk = [33])
    fun aCopyThatNeverReportsLeavesItsWindowOutAndItsBitmapAlone(): Unit =
        runBlocking {
            // The listener's looper never runs, so the copy never reports back.
            val listenerThread = HandlerThread("pixelcopy-listener").apply { start() }
            shadowOf(listenerThread.looper).pause()
            val bitmaps = RecordingBitmaps()
            val engine = engine(windows.showing(windows.activityWindow()), Handler(listenerThread.looper), bitmaps, copyTimeoutMillis = 50)

            assertThat(engine.captureFailure().message).isEqualTo("No visible root view")

            // The copy may still be writing to the screenshot's bitmap, which the activity was being
            // copied straight into, so it must not be recycled.
            assertThat(bitmaps.created).hasSize(1)
            assertThat(bitmaps.created.single().isRecycled).isFalse()
            listenerThread.quit()
        }

    private suspend fun ScreenshotCaptureEngine.captureFailure(): ScreenshotCaptureException =
        runCatching { captureDeviceCommandScreenshot() }.exceptionOrNull() as ScreenshotCaptureException

    private suspend fun ScreenshotCaptureEngine.captureDecoded(): Bitmap =
        captureDeviceCommandScreenshot().let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    private fun engine(
        windowManager: IWindowManager,
        listenerHandler: Handler = Handler(Looper.getMainLooper()),
        createBitmap: (Int, Int, Bitmap.Config) -> Bitmap = RecordingBitmaps(),
        copyTimeoutMillis: Long = ScreenshotCaptureEngine.COPY_TIMEOUT_MILLIS,
    ): ScreenshotCaptureEngine {
        val mainThreadHandler: MainThreadHandler = mock { on { mainHandler } doReturn listenerHandler }
        return ScreenshotCaptureEngine(mainThreadHandler, windowManager, createBitmap, copyTimeoutMillis = copyTimeoutMillis)
    }

    /**
     * Hands out bitmaps like the engine's default and remembers them, so a test can check they were
     * recycled. From the [immutableFrom]th bitmap on (counting from 0) they are immutable, which
     * PixelCopy refuses as a destination: the first bitmap is the screenshot's, the next ones are
     * copies of the windows drawn over the bottom one.
     */
    private class RecordingBitmaps(
        private val immutableFrom: Int = Int.MAX_VALUE,
    ) : (Int, Int, Bitmap.Config) -> Bitmap {
        val created = mutableListOf<Bitmap>()

        override fun invoke(
            width: Int,
            height: Int,
            config: Bitmap.Config,
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, config)
            val handedOut =
                if (created.size >= immutableFrom) bitmap.copy(config, false).also { bitmap.recycle() } else bitmap
            return handedOut.also { created += it }
        }
    }

    /** A sized view that belongs to no window. */
    private fun detachedView(): View = View(windows.activity).apply { layout(0, 0, 10, 10) }

    /** Lossy compression moves flat colours a little. */
    private fun assertColor(
        actual: Int,
        expected: Int,
    ) {
        val channels = listOf<(Int) -> Int>({ Color.red(it) }, { Color.green(it) }, { Color.blue(it) })
        val differences = channels.map { channel -> abs(channel(actual) - channel(expected)) }
        assertThat(differences).describedAs("pixel %x, expected %x", actual, expected).allMatch { it <= COLOR_TOLERANCE }
    }

    private companion object {
        const val COLOR_TOLERANCE = 24
    }
}
