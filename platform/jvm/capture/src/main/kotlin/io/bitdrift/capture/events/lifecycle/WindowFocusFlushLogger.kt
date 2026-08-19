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
 * Makes buffered data durable when the app's window loses focus.
 *
 * The gap this closes: the SDK's backgrounding flush hangs off `ProcessLifecycleOwner`'s `ON_STOP`,
 * and `ON_STOP` never fires for the app switcher — while the overview is showing, the recents
 * carousel renders a live tile of the activity, so the OS still reports it visible and the process is
 * genuinely not backgrounded. An app swiped away from the overview therefore loses whatever had not
 * yet reached disk. Window focus *does* drop in that case, which makes it the only observable signal.
 *
 * Focus loss is deliberately a broader signal than backgrounding. It also fires for an Activity
 * transition, rotation, the IME, a permission dialog and the notification shade. That is acceptable
 * because the flush is non-blocking and shared-core already gates what it costs: the stats upload sits
 * behind a minimum-interval floor and disk writes behind a debounce window. Over-calling is cheap;
 * missing the swipe-away is not.
 *
 * Note the asymmetry that follows from that: this flushes on focus *loss* only. Regaining focus needs
 * no durability action, and flushing on it would double the work for no benefit.
 *
 * Disabled remotely via [RuntimeFeature.LOGGER_FLUSHING_ON_WINDOW_FOCUS_LOSS]. The flag is re-read on
 * every callback rather than cached at [start], so a kill switch takes effect without a restart.
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
    /**
     * Main-thread confined, like everything else in this class: [start] and [stop] mutate it inside
     * [mainThreadHandler], and the focus callback that reads it is dispatched on the main thread.
     * Without it, a focus listener registered before [stop] would keep flushing a logger that is
     * being torn down — the runtime kill switch is independent of this listener's own lifecycle.
     */
    private var isStarted = false

    override fun start() {
        mainThreadHandler.run {
            isStarted = true
            application.registerActivityLifecycleCallbacks(this)
            // An activity already started before the SDK initialised will never see
            // onActivityStarted, so it would never get a listener and its focus loss would be
            // missed entirely. Found by testing: the same scenario flushed when the SDK happened to
            // start first and silently did nothing when it did not.
            windowManager.findFirstValidActivity()?.let(::registerFocusObserver)
        }
    }

    override fun stop() {
        mainThreadHandler.run {
            isStarted = false
            application.unregisterActivityLifecycleCallbacks(this)
            // The per-activity unregistrations ride on the callbacks just removed, so anything still
            // registered here would otherwise stay registered for the lifetime of its window.
            focusRegistrar.unregisterAll()
        }
    }

    /**
     * Registered from `onActivityStarted` rather than `onActivityCreated`: the decor view is
     * guaranteed to exist by then, and an activity that is created but never started cannot lose
     * focus, so there is nothing to observe yet.
     */
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
        // Defensive: a stopped activity is already unregistered, but an activity destroyed without a
        // matching stop would otherwise keep a listener alive on a dead window.
        focusRegistrar.unregister(activity)
    }

    private fun onWindowFocusLost() {
        if (!isStarted || !runtime.isEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_WINDOW_FOCUS_LOSS)) {
            return
        }
        // Non-blocking: this runs on the main thread, so waiting on the flush would risk an ANR at
        // exactly the moment the user is navigating away.
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
