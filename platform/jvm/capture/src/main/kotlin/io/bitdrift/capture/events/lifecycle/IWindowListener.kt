package io.bitdrift.capture.events.lifecycle

import android.view.Window

/**
 * Emits when a [android.view.Window] is available or removed
 */
interface IWindowListener {
    /**
     * Reports when [android.view.Window] is available
     */
    fun onWindowAvailable(window: Window)

    /**
     * Reports when the current window is not around
     */
    fun onWindowRemoved()
}
