// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands

import io.bitdrift.capture.Capture.Logger
import io.bitdrift.capture.experimental.ExperimentalBitdriftApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalBitdriftApi::class)
class CommandHandlerTest {
    @Test
    fun invoke_resolvesFromCompletableFuture() {
        val future = CompletableFuture<CommandResult>()
        val handle =
            Logger.registerCommand("cf_test_success") { _, _ ->
                future
            }

        val resultRef = AtomicReference<CommandResult>()
        val done = CountDownLatch(1)
        Logger.invokeCommandForTesting("cf_test_success") { result ->
            resultRef.set(result)
            done.countDown()
        }
        future.complete(CommandResult.Success(context = mapOf("ok" to "true")))

        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(resultRef.get()).isEqualTo(CommandResult.Success(context = mapOf("ok" to "true")))
        handle.unregister()
    }

    @Test
    fun unregister_cancelsTheUnderlyingCompletableFuture() {
        // Verified empirically, not assumed: kotlinx-coroutines-jdk8's `CompletableFuture.await()`
        // registers a cancellation handler that calls `future.cancel(false)` when the awaiting
        // coroutine is cancelled -- so unregister() cancelling the registration's Job propagates
        // all the way down to the CompletableFuture the handler returned, automatically.
        val future = CompletableFuture<CommandResult>()
        val handlerCalled = CountDownLatch(1)
        val handle =
            Logger.registerCommand("cf_test_cancel") { _, _ ->
                handlerCalled.countDown()
                future
            }

        Logger.invokeCommandForTesting("cf_test_cancel") { }
        assertThat(handlerCalled.await(1, TimeUnit.SECONDS)).isTrue()

        handle.unregister()
        Thread.sleep(100) // let cancellation propagation happen

        assertThat(future.isCancelled).isTrue()
    }
}
