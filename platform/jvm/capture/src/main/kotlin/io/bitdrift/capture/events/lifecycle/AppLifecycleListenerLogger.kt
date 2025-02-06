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
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger

internal class AppLifecycleListenerLogger(
    private val logger: LoggerImpl,
    private val processLifecycleOwner: LifecycleOwner,
    private val runtime: Runtime,
    private val backgroundThreadHandler: IBackgroundThreadHandler,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IEventListenerLogger,
    LifecycleEventObserver {
    private val lifecycleEventNames =
        hashMapOf(
            Lifecycle.Event.ON_CREATE to "AppCreate",
            Lifecycle.Event.ON_START to "AppStart",
            Lifecycle.Event.ON_RESUME to "AppResume",
            Lifecycle.Event.ON_PAUSE to "AppPause",
            Lifecycle.Event.ON_STOP to "AppStop",
            Lifecycle.Event.ON_DESTROY to "AppDestroy",
            Lifecycle.Event.ON_ANY to "AppAny",
        )

    override fun start() {
        mainThreadHandler.runAction {
            processLifecycleOwner.lifecycle.addObserver(this)
        }
    }

    override fun stop() {
        mainThreadHandler.runAction {
            processLifecycleOwner.lifecycle.removeObserver(this)
        }
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event,
    ) {
        backgroundThreadHandler.runAction execute@{
            if (!runtime.isEnabled(RuntimeFeature.APP_LIFECYCLE_EVENTS)) {
                return@execute
            }
            // refer to lifecycle states https://developer.android.com/topic/libraries/architecture/lifecycle#lc
            logger.log(
                LogType.LIFECYCLE,
                LogLevel.INFO,
            ) { "${lifecycleEventNames[event]}" }

            if (event == Lifecycle.Event.ON_STOP) {
                logger.flush(false)
            }
        }
    }
}
