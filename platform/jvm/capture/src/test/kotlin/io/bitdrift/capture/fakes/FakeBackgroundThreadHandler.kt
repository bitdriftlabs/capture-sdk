// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import com.google.common.util.concurrent.MoreExecutors
import io.bitdrift.capture.common.IBackgroundThreadHandler

/**
 * Fake [IBackgroundThreadHandler] that relies on newDirectExecutorService
 */
class FakeBackgroundThreadHandler : IBackgroundThreadHandler {
    private val fakeExecutorService = MoreExecutors.newDirectExecutorService()

    override fun runAsync(task: () -> Unit) {
        fakeExecutorService.execute(task)
    }
}
