// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decorator class that makes the internal delegate thread-safe and protects against repeated calls
 */
internal class SafeEventListenerLogger(private val listenerLogger: IEventListenerLogger) :
    IEventListenerLogger {

    private val isListening = AtomicBoolean(false)

    @Synchronized
    override fun start() {
        if (!isListening.getAndSet(true)) {
            listenerLogger.start()
        }
    }

    @Synchronized
    override fun stop() {
        if (isListening.getAndSet(false)) {
            listenerLogger.stop()
        }
    }
}
