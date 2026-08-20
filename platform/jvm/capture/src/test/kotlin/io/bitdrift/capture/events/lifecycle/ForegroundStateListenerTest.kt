// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.Mocks
import org.junit.Test

class ForegroundStateListenerTest {
    private val logger: IInternalLogger = mock()
    private val processLifecycleOwner: LifecycleOwner = mock()
    private val foregroundStateListener = ForegroundStateListener(logger, processLifecycleOwner, Mocks.sameThreadHandler)

    @Test
    fun updatesForegroundOnLifecycleChanges() {
        foregroundStateListener.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_START)
        foregroundStateListener.onStateChanged(processLifecycleOwner, Lifecycle.Event.ON_STOP)

        verify(logger).updateOotbField("foreground", "1")
        verify(logger).updateOotbField("foreground", "0")
    }
}
