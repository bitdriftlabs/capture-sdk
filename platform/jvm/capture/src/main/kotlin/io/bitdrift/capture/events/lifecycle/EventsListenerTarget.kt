// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import io.bitdrift.capture.IEventsListenerTarget
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.events.SafeEventListenerLogger

/**
 * A wrapper around platform event listeners that subscribe to various system notifications
 * and emit Capture out-of-the-box in response to them.
 */
internal class EventsListenerTarget : IEventsListenerTarget {
    private var listeners: MutableList<IEventListenerLogger> = mutableListOf()

    fun add(eventListener: IEventListenerLogger) {
        listeners.add(SafeEventListenerLogger(eventListener))
    }
    override fun start() {
        listeners.forEach { it.start() }
    }

    override fun stop() {
        listeners.forEach { it.stop() }
    }
}
