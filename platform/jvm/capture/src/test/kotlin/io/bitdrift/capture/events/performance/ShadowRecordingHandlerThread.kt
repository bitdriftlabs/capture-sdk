// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.HandlerThread
import android.os.Looper
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector
import java.util.Collections

/**
 * Records every [HandlerThread.getLooper] call made from the main thread.
 *
 * [HandlerThread.getLooper] blocks in `Object.wait()` until the new thread has prepared its
 * looper. When that call happens on the main thread and the new thread is slow to get scheduled
 * (for example on a loaded device during app startup), the main thread stalls and the app ANRs.
 */
@Implements(HandlerThread::class)
class ShadowRecordingHandlerThread {
    @RealObject
    private lateinit var realHandlerThread: HandlerThread

    @Implementation
    protected fun getLooper(): Looper? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mainThreadGetLooperCalls.add(realHandlerThread.name)
        }
        return reflector(HandlerThreadReflector::class.java, realHandlerThread).getLooper()
    }

    @ForType(HandlerThread::class)
    private interface HandlerThreadReflector {
        @Direct
        fun getLooper(): Looper?
    }

    companion object {
        val mainThreadGetLooperCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    }
}
