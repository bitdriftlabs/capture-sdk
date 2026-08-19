// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.common.IWindowManager
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger

/**
 * Flushes the logger when the app's window loses focus — the only signal that fires for the app
 * switcher, where `ON_STOP` never does because the activity stays visible. Focus loss also fires for
 * transient cases (IME, dialogs, transitions); over-calling is fine since flushes are debounced
 * downstream.
 *
 * This will be a no-op when the `client_feature.android.logger_flushing_on_window_focus_loss` kill
 * switch is disabled.
 */
internal class WindowFocusFlushLogger(
    private val application: Application,
    private val logger: IInternalLogger,
    private val runtime: Runtime,
    private val windowManager: IWindowManager,
    private val focusRegistrar: IWindowFocusRegistrar = ViewTreeWindowFocusRegistrar(),
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IEventListenerLogger,
    Application.ActivityLifecycleCallbacks {
    // Main-thread confined. Gates flushes from focus listeners that outlive stop().
    private var isStarted = false

    override fun start() {
        mainThreadHandler.run {
            isStarted = true
            application.registerActivityLifecycleCallbacks(this)
            // An activity started before the SDK never sees onActivityStarted; pick it up here.
            windowManager.findFirstValidActivity()?.let(::registerFocusObserver)
        }
    }

    override fun stop() {
        mainThreadHandler.run {
            isStarted = false
            application.unregisterActivityLifecycleCallbacks(this)
            // Per-activity unregistration rides on the callbacks just removed.
            focusRegistrar.unregisterAll()
        }
    }

    // Registered on start rather than create: the decor view is guaranteed to exist by then.
    override fun onActivityStarted(activity: Activity) {
        registerFocusObserver(activity)
    }

    private fun registerFocusObserver(activity: Activity) {
        focusRegistrar.register(activity) { hasFocus ->
            if (!hasFocus) {
                onWindowFocusLost()
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        focusRegistrar.unregister(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Defensive: covers an activity destroyed without a matching stop.
        focusRegistrar.unregister(activity)
    }

    private fun onWindowFocusLost() {
        if (!isStarted || !runtime.isEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_WINDOW_FOCUS_LOSS)) {
            return
        }
        // Focus callbacks are dispatched on the main thread; a blocking flush would stall it.
        logger.flush(blocking = false)
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit
}
