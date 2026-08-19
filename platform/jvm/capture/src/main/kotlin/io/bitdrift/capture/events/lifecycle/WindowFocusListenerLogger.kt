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
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger

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
    ViewTreeObserver.OnWindowFocusChangeListener,
    View.OnAttachStateChangeListener {

    private var isStarted = false

    private val lifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            @UiThread
            override fun onActivityStarted(activity: Activity) {
                if (!isStarted) return
                val rootView = activity.window?.decorView?.rootView ?: return

                rootView.removeOnAttachStateChangeListener(this@WindowFocusListenerLogger)
                rootView.addOnAttachStateChangeListener(this@WindowFocusListenerLogger)

                rootView.onViewTreeObserverReady { viewTreeObserver ->
                    viewTreeObserver.removeOnWindowFocusChangeListener(this@WindowFocusListenerLogger)
                    viewTreeObserver.addOnWindowFocusChangeListener(this@WindowFocusListenerLogger)
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                // Focus listeners are removed when the view is detached.
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
        }

    override fun start() {
        mainThreadHandler.run {
            if (!isStarted) {
                isStarted = true
                application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            }
        }
    }

    override fun stop() {
        mainThreadHandler.run {
            if (isStarted) {
                isStarted = false
                application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            }
        }
    }

    @UiThread
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!isStarted || hasFocus) {
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

    override fun onViewAttachedToWindow(view: View) {
        // Handled by onViewTreeObserverReady in onActivityStarted
    }

    override fun onViewDetachedFromWindow(view: View) {
        view.removeWindowFocusListener()
        view.removeOnAttachStateChangeListener(this)
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
