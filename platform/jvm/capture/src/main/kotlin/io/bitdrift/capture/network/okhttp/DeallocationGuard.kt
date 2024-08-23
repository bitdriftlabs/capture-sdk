// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.network.okhttp

import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe

/**
 * Utility helper that makes it safer to work with native handles. This class takes a protected inner value
 * and a deallocation function, providing access to the inner value guarded by whether deallocation has happened.
 *
 * As soon as deallocate() is called, the deallocation function will be invoked and the inner value becomes inaccessible.
 */
@ThreadSafe
internal class DeallocationGuard<T>(private val inner: T, private val deallocator: (T) -> Unit) {
    // Note that this must be protected via @Synchronized blocks, as a `deallocate` call and a interaction
    // could both check an AtomicBool and both see `deallocated = false`, resulting in the calls overlapping
    // and causing undefined behavior.
    //
    // This originally used a finalizer function which would have deallocated the memory at the right
    // time, but production testing revealed that the finalizer task would time out at times; this was
    // never root caused.
    //
    // Once we are fully on Android 33 we can leverage https://developer.android.com/reference/java/lang/ref/Cleaner
    // to perform automatic cleanup based on object lifecycle, which allows us to safely invoke the deallocation function
    // once an object is no longer in use.
    @GuardedBy("this")
    private var deallocated: Boolean = false

    /**
     * Invokes the provided function with the guarded value, if deallocation has not yet happened.
     * @param f function to invoke
     */
    @Synchronized
    fun safeAccess(f: (T) -> Unit) {
        if (!deallocated) {
            f(inner)
        }
    }

    /**
     * Invokes the deallocation function for the guarded value. Further calls to safeAccess will no
     * longer invoke the provided function.
     */
    @Synchronized
    fun deallocate() {
        if (!deallocated) {
            deallocated = true
            deallocator(inner)
        }
    }
}
