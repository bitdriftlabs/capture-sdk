// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.CaptureTestJniLibrary
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.commands.CommandResult
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.replay.SessionReplayConfiguration
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SessionReplayTargetTest {
    private val logger: LoggerImpl = mock()
    private val errorHandler: ErrorHandler = mock()
    private val handler: MainThreadHandler = Mocks.sameThreadHandler
    private val windowManager: IWindowManager = mock()

    private val target = createTarget()

    private fun createTarget(captureScreenshot: (() -> Unit)? = null) =
        SessionReplayTarget(
            SessionReplayConfiguration(),
            errorHandler,
            ApplicationProvider.getApplicationContext(),
            logger,
            handler,
            windowManager,
            captureScreenshot,
        )

    init {
        CaptureJniLibrary.load()
    }

    @Test
    fun sessionReplayTargetDoesNotCrash() {
        CaptureTestJniLibrary.runSessionReplayTargetTest(target)
    }

    @Test
    fun sessionReplayTargetEmitsScreenLog() {
        target.captureScreen()
        // TODO: Make this test work, the issue is that in test environment session replay
        //  sees 0 views and as a result it doesn't emit a session replay screen log.
//        verify(logger, timeout(1000).times(1)).logSessionReplayScreen(any(), any())
    }

    @Test
    fun commandScreenshotValidatesJpegAndSize() =
        runBlocking {
            val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 0xff.toByte(), 0xd9.toByte())
            val target = createTarget {}
            for (bytes in listOf(jpeg, byteArrayOf(), byteArrayOf(1, 2, 3, 4), ByteArray(2 * 1024 * 1024 + 1))) {
                val capture = async(start = CoroutineStart.UNDISPATCHED) { target.captureCommandScreenshot() }
                target.onDeviceCommandScreenshotCaptured(bytes)
                val result = capture.await()
                if (bytes === jpeg) {
                    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
                    val attachment = (result as CommandResult.Success).attachment!!
                    assertThat(attachment.bytes).isEqualTo(jpeg)
                    assertThat(attachment.contentType).isEqualTo("image/jpeg")
                } else {
                    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
                }
            }
        }

    @Test
    fun cancelledScreenshotKeepsPhysicalLatchUntilItsCallback(): Unit =
        runBlocking {
            var captures = 0
            val target = createTarget { captures++ }
            val first = async(start = CoroutineStart.UNDISPATCHED) { target.captureCommandScreenshot() }
            first.cancelAndJoin()

            val busy = target.captureCommandScreenshot()
            assertThat((busy as CommandResult.Error).title).isEqualTo("screenshot capture already in progress")
            assertThat(captures).isEqualTo(1)

            target.onDeviceCommandScreenshotCaptured(byteArrayOf())
            val second = async(start = CoroutineStart.UNDISPATCHED) { target.captureCommandScreenshot() }
            assertThat(second.isCompleted).isFalse()
            assertThat(captures).isEqualTo(2)
            target.onDeviceCommandScreenshotCaptured(byteArrayOf())
            assertThat(second.await()).isInstanceOf(CommandResult.Error::class.java)
        }

    @Test
    fun synchronousCaptureFailureReleasesPhysicalLatch(): Unit =
        runBlocking {
            var captures = 0
            val target =
                createTarget {
                    captures++
                    error("capture failed")
                }
            repeat(2) {
                val result = target.captureCommandScreenshot()
                assertThat((result as CommandResult.Error).title).isEqualTo("screenshot capture failed")
            }
            assertThat(captures).isEqualTo(2)
        }
}
