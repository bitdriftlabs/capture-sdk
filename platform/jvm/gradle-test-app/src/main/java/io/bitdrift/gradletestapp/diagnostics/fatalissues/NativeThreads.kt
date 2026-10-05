// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.diagnostics.fatalissues

/**
 * Spawns pthreads that are never attached to the JVM, so they are only visible to the kernel.
 */
object NativeThreads {
    init {
        System.loadLibrary("gradletestapp_native")
    }

    /**
     * Creates up to [count] detached, permanently blocked native threads and returns how many were created.
     */
    @JvmStatic
    external fun spawn(count: Int): Int
}
