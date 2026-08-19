// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.common.MainThreadHandler

/**
 * Keeps the foreground OOTB field current independently of configurable lifecycle-event logging.
 */
internal class ForegroundStateListener(
    private val logger: IInternalLogger,
    private val processLifecycleOwner: LifecycleOwner,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : LifecycleEventObserver {
    fun start() {
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.addObserver(this)
        }
    }

    fun stop() {
        mainThreadHandler.run {
            processLifecycleOwner.lifecycle.removeObserver(this)
        }
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        when (event) {
            Lifecycle.Event.ON_START -> logger.updateOotbField(ClientAttributes.FOREGROUND_KEY, "1")
            Lifecycle.Event.ON_STOP -> logger.updateOotbField(ClientAttributes.FOREGROUND_KEY, "0")
            else -> Unit
        }
    }
}
