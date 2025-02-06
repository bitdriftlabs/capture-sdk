// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.common

import java.util.concurrent.ExecutorService

/**
 * Runs the specified actions off main thread
 */
interface IBackgroundThreadHandler {
    /**
     * Runs the specified action on a background thread
     */
    fun runAction(action: () -> Unit)

    /**
     * Shuts down the background thread handler
     */
    fun shutdown()

    /**
     * Readable thread name
     */
    fun threadName(): String

    /**
     * Associated [ExecutorService] to this handler
     */
    fun asExecutorService(): ExecutorService
}
