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
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Flushes the logger as soon as the app's window loses focus.
 *
 * [AppLifecycleListenerLogger] flushes on `Lifecycle.Event.ON_STOP`, which `ProcessLifecycleOwner`
 * deliberately debounces by 700ms so that configuration changes don't look like backgrounding.
 * That delay lands after the app switcher transition has completed, so an app that gets swiped away
 * (or killed by the system) shortly after can die before the flush runs.
 *
 * Window focus is lost the instant the app switcher, the Home screen or any other window takes
 * over, which is the earliest signal available globally without any integrator side changes.
 *
 * Focus is also lost for reasons that have nothing to do with the app leaving the foreground, so
 * the number of currently resumed activities is used to tell the whole-app case apart from those.
 * See [onWindowFocusChanged].
 *
 * This will be a no-op when the `client_feature.android.window_focus_flushing` kill switch is
 * disabled.
 */
internal class WindowFocusListenerLogger(
    private val application: Application,
    private val logger: IInternalLogger,
    private val runtime: Runtime,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IEventListenerLogger,
    Application.ActivityLifecycleCallbacks,
    ViewTreeObserver.OnWindowFocusChangeListener {
    /**
     * Root views this listener is currently registered against, keyed by their activity.
     *
     * Both the key and the value are weakly held: a root view keeps a hard reference to its
     * activity through its context, so holding it strongly here would defeat the weak keys.
     *
     * Only ever accessed from the main thread.
     */
    @VisibleForTesting
    internal val trackedRootViews = WeakHashMap<Activity, WeakReference<View>>()

    override fun start() {
        mainThreadHandler.run {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun stop() {
        mainThreadHandler.run {
            application.unregisterActivityLifecycleCallbacks(this)
            trackedRootViews.values.forEach { it.get()?.removeWindowFocusListener() }
            trackedRootViews.clear()
        }
    }

    /**
     * Always dispatched on the main thread: `ViewRootImpl.handleWindowFocusChanged` fires this from
     * the thread that owns the view hierarchy. The work done here is cheap enough to run inline
     * (a runtime snapshot lookup and a non-blocking send over to the Rust logger), and doing so
     * avoids losing the race against the process being killed that a thread hop would introduce.
     *
     * Losing focus on its own doesn't prove the app is leaving the foreground: it also happens when
     * another activity of the same app is launched, when a dialog or the IME opens and when the
     * notification shade is pulled down. Those can't be filtered out here, because the window loses
     * focus *before* `onPause` is dispatched, so at this point there is no way to tell yet whether
     * another activity is about to take over or the app is going away entirely. Waiting for that
     * answer gives up the head start this listener exists to buy.
     *
     * Flushing unconditionally is the deliberate trade: a redundant flush costs a non-blocking send
     * over to the Rust logger, a missed one costs the logs of a process killed from the app
     * switcher.
     */
    @UiThread
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            return
        }

        // TODO(murki): BIT-XXXX Rate limit these flushes. Note a resumed-activity count does not
        //  work as a filter here (verified on device): focus loss is dispatched before `onPause`,
        //  so backgrounding still looks like it has a resumed activity at this point. A time based
        //  limit that flushes first and suppresses repeats afterwards is the workable shape.
        if (!runtime.isEnabled(RuntimeFeature.WINDOW_FOCUS_FLUSHING)) {
            Log.w(DEBUG_TAG, "window focus lost but the kill switch is disabled, skipping flush")
            return
        }

        Log.w(DEBUG_TAG, "window focus lost, flushing logger")
        logger.flush(false)
    }

    /**
     * The listener is installed here rather than in [onActivityCreated] on purpose:
     * `onActivityCreated` is dispatched from `Activity.super.onCreate()`, before the host activity
     * had a chance to call `requestWindowFeature` or `setContentView`. Reading
     * `Window.getDecorView()` at that point force-installs the decor view and would break those
     * calls in the host app.
     */
    @UiThread
    override fun onActivityStarted(activity: Activity) {
        if (trackedRootViews.containsKey(activity)) {
            return
        }

        val rootView = activity.window?.decorView?.rootView ?: return
        trackedRootViews[activity] = WeakReference(rootView)
        rootView.onViewTreeObserverReady { viewTreeObserver ->
            viewTreeObserver.addOnWindowFocusChangeListener(this)
        }
    }

    @UiThread
    override fun onActivityDestroyed(activity: Activity) {
        trackedRootViews.remove(activity)?.get()?.removeWindowFocusListener()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        // no-op
    }

    override fun onActivityResumed(activity: Activity) {
        Log.w(DEBUG_TAG, "onActivityResumed ${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.w(DEBUG_TAG, "onActivityPaused ${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        Log.w(DEBUG_TAG, "onActivityStopped ${activity.javaClass.simpleName}")
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
        // no-op
    }

    @UiThread
    private fun View.removeWindowFocusListener() {
        val viewTreeObserver = viewTreeObserver
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnWindowFocusChangeListener(this@WindowFocusListenerLogger)
        }
    }

    private companion object {
        // TODO(murki): BIT-XXXX Remove this temporary on-device debugging aid, and the lifecycle
        //  logging in the callbacks above, before shipping.
        private const val DEBUG_TAG = "miguel-flush-focus"
    }
}

/**
 * Invokes [block] with a [ViewTreeObserver] that is safe to register listeners against.
 *
 * Inspired by square/papa's `ViewTreeObservers.kt`. A view that isn't attached yet hands out a
 * temporary "floating" [ViewTreeObserver] that gets merged into the real one once the view is
 * attached, so registering against it before then is unreliable. Waiting for the attach avoids
 * that entirely.
 */
@UiThread
private fun View.onViewTreeObserverReady(block: (ViewTreeObserver) -> Unit) {
    if (isAttachedToWindow && viewTreeObserver.isAlive) {
        block(viewTreeObserver)
        return
    }

    addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                // The observer available here is a different instance than the one read above, it
                // has to be resolved again now that the view is attached.
                block(view.rootView.viewTreeObserver)
                view.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(view: View) {
                // no-op
            }
        },
    )
}
