// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.Looper

/**
 * Runs work on the main thread once it has no pending messages.
 */
internal fun interface IMainThreadIdleScheduler {
    fun runWhenIdle(action: () -> Unit)
}

/**
 * [IMainThreadIdleScheduler] backed by a main-thread [android.os.MessageQueue.IdleHandler], so the
 * action never delays a message that is already queued (such as the first frame of an activity).
 */
internal class MainThreadIdleScheduler : IMainThreadIdleScheduler {
    override fun runWhenIdle(action: () -> Unit) {
        Looper.getMainLooper().queue.addIdleHandler {
            action()
            false
        }
    }
}
