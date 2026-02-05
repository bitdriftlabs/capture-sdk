// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.interaction

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.UiThread
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.common.MainThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.IEventListenerLogger
import io.bitdrift.capture.providers.fieldsOfOptional
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService

internal class TouchInteractionListener(
    private val application: Application,
    private val logger: IInternalLogger,
    private val runtime: Runtime,
    private val executor: ExecutorService,
    private val mainThreadHandler: MainThreadHandler = MainThreadHandler(),
) : IEventListenerLogger, Application.ActivityLifecycleCallbacks {

    private val wrappedCallbacks = WeakHashMap<Window, WeakReference<TouchTrackingWindowCallback>>()

    override fun start() {
        mainThreadHandler.run {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun stop() {
        mainThreadHandler.run {
            application.unregisterActivityLifecycleCallbacks(this)
            wrappedCallbacks.clear()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // no-op
    }

    override fun onActivityStarted(activity: Activity) {
        // no-op
    }

    override fun onActivityResumed(activity: Activity) {
        if (!runtime.isEnabled(RuntimeFeature.TOUCH_INTERACTION_EVENTS)) {
            return
        }
        wrapWindowCallback(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        // no-op, keep callback wrapped to handle edge cases
    }

    override fun onActivityStopped(activity: Activity) {
        // no-op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // no-op
    }

    override fun onActivityDestroyed(activity: Activity) {
        unwrapWindowCallback(activity)
    }

    @UiThread
    private fun wrapWindowCallback(activity: Activity) {
        val window = activity.window ?: return
        val existingCallback = window.callback ?: return

        if (existingCallback is TouchTrackingWindowCallback) {
            return
        }

        val alreadyWrapped = wrappedCallbacks[window]?.get()
        if (alreadyWrapped != null) {
            return
        }

        val wrappedCallback = TouchTrackingWindowCallback(
            delegate = existingCallback,
            activityName = activity.javaClass.simpleName,
            window = window,
            onTouchAction = { touchData -> logTouchInteraction(touchData) },
        )
        window.callback = wrappedCallback
        wrappedCallbacks[window] = WeakReference(wrappedCallback)
    }

    @UiThread
    private fun unwrapWindowCallback(activity: Activity) {
        val window = activity.window ?: return
        val currentCallback = window.callback

        if (currentCallback is TouchTrackingWindowCallback) {
            window.callback = currentCallback.delegate
        }
        wrappedCallbacks.remove(window)
    }

    private fun logTouchInteraction(touchData: TouchData) {
        executor.execute {
            if (!runtime.isEnabled(RuntimeFeature.TOUCH_INTERACTION_EVENTS)) {
                return@execute
            }

            val fields = fieldsOfOptional(
                "_activity" to touchData.activityName,
                "_view_type" to touchData.viewType,
                "_view_class" to touchData.viewClass,
                "_view_id" to touchData.viewId,
                "_view_tag" to touchData.viewTag,
                "_content_description" to touchData.contentDescription,
                "_text" to touchData.text,
                "_x" to touchData.x.toString(),
                "_y" to touchData.y.toString(),
            )

            logger.logInternal(
                LogType.UX,
                LogLevel.DEBUG,
                fields,
            ) { TOUCH_INTERACTION_MESSAGE }
        }
    }

    private companion object {
        private const val TOUCH_INTERACTION_MESSAGE = "TouchInteraction"
    }
}

internal data class TouchData(
    val activityName: String,
    val viewType: String?,
    val viewClass: String?,
    val viewId: String?,
    val viewTag: String?,
    val contentDescription: String?,
    val text: String?,
    val x: Float,
    val y: Float,
)

internal class TouchTrackingWindowCallback(
    val delegate: Window.Callback,
    private val activityName: String,
    private val window: Window,
    private val onTouchAction: (TouchData) -> Unit,
) : Window.Callback by delegate {

    private val hitRect = Rect()
    private val locationBuffer = IntArray(2)

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && event.actionMasked == MotionEvent.ACTION_UP) {
            val x = event.rawX
            val y = event.rawY
            val touchedView = findInteractiveViewAtPosition(window.decorView, x.toInt(), y.toInt())
            if (touchedView != null) {
                val touchData = extractTouchData(touchedView, x, y)
                onTouchAction(touchData)
            }
        }
        return delegate.dispatchTouchEvent(event)
    }

    private fun findInteractiveViewAtPosition(root: View, x: Int, y: Int): View? {
        root.getLocationOnScreen(locationBuffer)
        val offsetX = x - locationBuffer[0]
        val offsetY = y - locationBuffer[1]
        return findInteractiveViewRecursive(root, offsetX, offsetY)
    }

    private fun findInteractiveViewRecursive(view: View, x: Int, y: Int): View? {
        if (view.visibility != View.VISIBLE) {
            return null
        }

        view.getHitRect(hitRect)
        if (!hitRect.contains(x + view.left, y + view.top)) {
            return null
        }

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val childX = x - child.left + view.scrollX
                val childY = y - child.top + view.scrollY
                val found = findInteractiveViewRecursive(child, childX, childY)
                if (found != null) {
                    return found
                }
            }
        }

        return if (isInteractiveView(view)) view else null
    }

    private fun isInteractiveView(view: View): Boolean {
        if (!view.isClickable && !view.isFocusable) {
            return false
        }
        return when (view) {
            is Button,
            is ImageButton,
            is CheckBox,
            is RadioButton,
            is Switch,
            is ToggleButton,
            is CompoundButton,
            is EditText,
            -> true
            else -> view.isClickable
        }
    }

    private fun extractTouchData(view: View, x: Float, y: Float): TouchData {
        return TouchData(
            activityName = activityName,
            viewType = getViewType(view),
            viewClass = view.javaClass.simpleName,
            viewId = getViewIdName(view),
            viewTag = view.tag?.toString()?.take(MAX_TAG_LENGTH),
            contentDescription = view.contentDescription?.toString()?.take(MAX_CONTENT_DESC_LENGTH),
            text = getViewText(view)?.take(MAX_TEXT_LENGTH),
            x = x,
            y = y,
        )
    }

    private fun getViewType(view: View): String {
        return when (view) {
            is CheckBox -> "CheckBox"
            is RadioButton -> "RadioButton"
            is Switch -> "Switch"
            is ToggleButton -> "ToggleButton"
            is CompoundButton -> "CompoundButton"
            is ImageButton -> "ImageButton"
            is Button -> "Button"
            is EditText -> "EditText"
            is TextView -> "TextView"
            else -> "View"
        }
    }

    private fun getViewIdName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            null
        }
    }

    private fun getViewText(view: View): String? {
        return when (view) {
            is TextView -> view.text?.toString()
            is CompoundButton -> view.text?.toString()
            else -> null
        }
    }

    private companion object {
        private const val MAX_TAG_LENGTH = 100
        private const val MAX_CONTENT_DESC_LENGTH = 200
        private const val MAX_TEXT_LENGTH = 100
    }
}
