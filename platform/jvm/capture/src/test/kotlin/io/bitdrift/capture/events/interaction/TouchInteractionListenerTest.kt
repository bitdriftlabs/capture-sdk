// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.interaction

import android.app.Activity
import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.Mocks
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.ArrayFields
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class TouchInteractionListenerTest {
    private val logger: IInternalLogger = mock()
    private val runtime: Runtime = mock()
    private val mainThreadHandler = Mocks.sameThreadHandler
    private val executor: ExecutorService = MoreExecutors.newDirectExecutorService()

    private lateinit var application: Application
    private lateinit var activity: Activity
    private lateinit var window: Window
    private lateinit var touchInteractionListener: TouchInteractionListener

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        window = activity.window

        whenever(runtime.isEnabled(RuntimeFeature.TOUCH_INTERACTION_EVENTS)).thenReturn(true)

        touchInteractionListener = TouchInteractionListener(
            application = application,
            logger = logger,
            runtime = runtime,
            executor = executor,
            mainThreadHandler = mainThreadHandler,
        )
    }

    @Test
    fun onActivityResumed_shouldWrapWindowCallback() {
        val originalCallback = window.callback

        touchInteractionListener.onActivityResumed(activity)

        assertThat(window.callback).isInstanceOf(TouchTrackingWindowCallback::class.java)
        assertThat((window.callback as TouchTrackingWindowCallback).delegate).isEqualTo(originalCallback)
    }

    @Test
    fun onActivityResumed_withFeatureDisabled_shouldNotWrapCallback() {
        whenever(runtime.isEnabled(RuntimeFeature.TOUCH_INTERACTION_EVENTS)).thenReturn(false)
        val originalCallback = window.callback

        touchInteractionListener.onActivityResumed(activity)

        assertThat(window.callback).isEqualTo(originalCallback)
    }

    @Test
    fun onActivityDestroyed_shouldUnwrapWindowCallback() {
        touchInteractionListener.onActivityResumed(activity)
        assertThat(window.callback).isInstanceOf(TouchTrackingWindowCallback::class.java)

        touchInteractionListener.onActivityDestroyed(activity)

        assertThat(window.callback).isNotInstanceOf(TouchTrackingWindowCallback::class.java)
    }

    @Test
    fun onActivityResumed_calledTwice_shouldNotDoubleWrap() {
        touchInteractionListener.onActivityResumed(activity)
        val firstWrapped = window.callback

        touchInteractionListener.onActivityResumed(activity)

        assertThat(window.callback).isSameAs(firstWrapped)
    }

    @Test
    fun dispatchTouchEvent_withActionUp_onInteractiveView_shouldLogTouchInteraction() {
        val button = Button(activity).apply {
            id = View.generateViewId()
            isClickable = true
        }
        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        contentView.addView(button)

        touchInteractionListener.onActivityResumed(activity)

        val location = IntArray(2)
        button.getLocationOnScreen(location)
        val motionEvent = MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_UP,
            location[0].toFloat() + button.width / 2,
            location[1].toFloat() + button.height / 2,
            0,
        )

        window.callback.dispatchTouchEvent(motionEvent)

        verify(logger).logInternal(
            eq(LogType.UX),
            eq(LogLevel.DEBUG),
            argThat<ArrayFields> { fields ->
                fields["_view_type"] == "Button" &&
                    fields["_activity"] == "Activity"
            },
            any(),
            any(),
            any(),
            argThat { message: () -> String -> message.invoke() == "TouchInteraction" },
        )

        motionEvent.recycle()
    }

    @Test
    fun dispatchTouchEvent_withActionDown_shouldNotLog() {
        val button = Button(activity).apply {
            isClickable = true
        }
        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        contentView.addView(button)

        touchInteractionListener.onActivityResumed(activity)

        val location = IntArray(2)
        button.getLocationOnScreen(location)
        val motionEvent = MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_DOWN,
            location[0].toFloat() + button.width / 2,
            location[1].toFloat() + button.height / 2,
            0,
        )

        window.callback.dispatchTouchEvent(motionEvent)

        verify(logger, never()).logInternal(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )

        motionEvent.recycle()
    }

    @Test
    fun dispatchTouchEvent_withFeatureDisabled_shouldNotLog() {
        val button = Button(activity).apply {
            isClickable = true
        }
        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        contentView.addView(button)

        touchInteractionListener.onActivityResumed(activity)

        whenever(runtime.isEnabled(RuntimeFeature.TOUCH_INTERACTION_EVENTS)).thenReturn(false)

        val location = IntArray(2)
        button.getLocationOnScreen(location)
        val motionEvent = MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_UP,
            location[0].toFloat() + button.width / 2,
            location[1].toFloat() + button.height / 2,
            0,
        )

        window.callback.dispatchTouchEvent(motionEvent)

        verify(logger, never()).logInternal(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )

        motionEvent.recycle()
    }

    @Test
    fun dispatchTouchEvent_onNonInteractiveView_shouldNotLog() {
        val nonInteractiveView = View(activity).apply {
            isClickable = false
            isFocusable = false
        }
        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        contentView.addView(nonInteractiveView)

        touchInteractionListener.onActivityResumed(activity)

        val location = IntArray(2)
        nonInteractiveView.getLocationOnScreen(location)
        val motionEvent = MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_UP,
            location[0].toFloat() + 1,
            location[1].toFloat() + 1,
            0,
        )

        window.callback.dispatchTouchEvent(motionEvent)

        verify(logger, never()).logInternal(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )

        motionEvent.recycle()
    }

    @Test
    fun dispatchTouchEvent_shouldPassEventToDelegate() {
        touchInteractionListener.onActivityResumed(activity)
        assertThat(window.callback).isInstanceOf(TouchTrackingWindowCallback::class.java)

        val motionEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 0f, 0f, 0)
        val result = window.callback.dispatchTouchEvent(motionEvent)

        assertThat(result).isTrue()
        motionEvent.recycle()
    }
}
