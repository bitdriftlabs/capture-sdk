// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import io.bitdrift.capture.CaptureJniLibrary
import io.bitdrift.capture.CaptureTestJniLibrary
import org.junit.Test

class EventsListenerTargetTest {
    private val listener = EventsListenerTarget()

    init {
        CaptureJniLibrary.load()
    }

    @Test
    fun eventsListenerTargetDoesNotCrash() {
        CaptureTestJniLibrary.runEventsListenerTargetTest(listener)
    }
}
