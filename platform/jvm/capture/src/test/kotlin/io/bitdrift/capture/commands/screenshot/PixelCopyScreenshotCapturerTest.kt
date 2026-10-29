// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.screenshot

import io.bitdrift.capture.commands.CommandResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PixelCopyScreenshotCapturerTest {
    @Test
    fun screenshotDefersPayloadValidationToRust(): Unit =
        runBlocking {
            for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3, 4), ByteArray(2 * 1024 * 1024 + 1))) {
                val result = PixelCopyScreenshotCapturer { bytes }.capture()
                if (bytes.isNotEmpty()) {
                    val attachment = (result as CommandResult.Success).attachment!!
                    assertThat(attachment.bytes).isEqualTo(bytes)
                    assertThat(attachment.contentType).isEqualTo("image/jpeg")
                } else {
                    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
                }
            }
        }

    @Test
    fun cancelledScreenshotWaitsForTheInFlightCopy(): Unit =
        runBlocking {
            val platform = CompletableDeferred<ByteArray>()
            val capturer = PixelCopyScreenshotCapturer { platform.await() }
            val capture = async(start = CoroutineStart.UNDISPATCHED) { capturer.capture() }

            capture.cancel()
            yield()
            assertThat(capture.isCompleted).isFalse()

            platform.complete(byteArrayOf(1))
            yield()
            assertThat(capture.isCompleted).isTrue()
        }

    @Test
    fun captureFailureBecomesACommandError(): Unit =
        runBlocking {
            var captures = 0
            val capturer =
                PixelCopyScreenshotCapturer {
                    captures++
                    error("capture failed")
                }
            repeat(2) {
                val result = capturer.capture()
                assertThat((result as CommandResult.Error).title).isEqualTo("screenshot capture failed")
            }
            assertThat(captures).isEqualTo(2)
        }
}
