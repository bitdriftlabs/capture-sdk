// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import io.bitdrift.capture.common.IBackgroundThreadHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * BackgroundThreadHandler that can be used for UI tests
 */
class FakeBackgroundThreadHandler : IBackgroundThreadHandler {
    private val testExecutorService = Executors.newSingleThreadExecutor()

    override fun runAction(action: () -> Unit) {
        testExecutorService.execute(action)
    }

    override fun shutdown() {
        testExecutorService.shutdown()
    }

    override fun threadName(): String = "fake-background-thread-handler"

    override fun asExecutorService(): ExecutorService = testExecutorService
}