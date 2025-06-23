// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.fakes

import io.bitdrift.capture.ILogger
import io.bitdrift.capture.IPreInitLogFlusher

/**
 * Fake [IPreInitLogFlusher] to ease testing
 */
class FakePreInitLogFlusher : IPreInitLogFlusher {
    private var _wasFlushed = false
    val wasFlushed: Boolean
        get() = _wasFlushed

    override fun flushToNative(loggerImpl: ILogger) {
        _wasFlushed = true
    }

    fun reset() {
        _wasFlushed = false
    }
}
