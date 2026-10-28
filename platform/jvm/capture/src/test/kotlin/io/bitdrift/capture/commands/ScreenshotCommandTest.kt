// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ScreenshotCommandTest {
    @Test
    fun screenshotDefersPayloadValidationToRust(): Unit =
        runBlocking {
            var callback: ((ByteArray) -> Unit)? = null
            val command = ScreenshotCommand { callback = it }
            for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3, 4), ByteArray(2 * 1024 * 1024 + 1))) {
                val capture = async(start = CoroutineStart.UNDISPATCHED) { command.execute() }
                checkNotNull(callback)(bytes)
                val result = capture.await()
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
    fun cancelledScreenshotKeepsPhysicalLatchUntilItsCallback(): Unit =
        runBlocking {
            val callbacks = mutableListOf<(ByteArray) -> Unit>()
            val command = ScreenshotCommand { callbacks.add(it) }
            val first = async(start = CoroutineStart.UNDISPATCHED) { command.execute() }
            first.cancelAndJoin()

            val busy = command.execute()
            assertThat((busy as CommandResult.Error).title).isEqualTo("screenshot capture already in progress")
            assertThat(busy.code).isEqualTo(CommandErrorCode.CommandAlreadyExecuting)
            assertThat(callbacks).hasSize(1)

            callbacks[0](byteArrayOf())
            val second = async(start = CoroutineStart.UNDISPATCHED) { command.execute() }
            assertThat(second.isCompleted).isFalse()
            assertThat(callbacks).hasSize(2)
            callbacks[0](byteArrayOf(1))
            assertThat(second.isCompleted).isFalse()
            callbacks[1](byteArrayOf())
            assertThat(second.await()).isInstanceOf(CommandResult.Error::class.java)
        }

    @Test
    fun synchronousCaptureFailureReleasesPhysicalLatch(): Unit =
        runBlocking {
            var captures = 0
            val command =
                ScreenshotCommand {
                    captures++
                    error("capture failed")
                }
            repeat(2) {
                val result = command.execute()
                assertThat((result as CommandResult.Error).title).isEqualTo("screenshot capture failed")
            }
            assertThat(captures).isEqualTo(2)
        }
}
