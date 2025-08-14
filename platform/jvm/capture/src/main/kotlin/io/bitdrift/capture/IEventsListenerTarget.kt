// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Responsible for managing event listeners that emit out-of-the-box logs in response
 * to various system notifications and events.
 */
interface IEventSubscriber {
    /**
     * Starts the event listener, giving it an opportunity to subscribe to notifications
     * and system events.
     */
    fun start()

    /**
     * Stops the event listener, giving it an opportunity to unsubscribe from notifications
     * and system events it previously subscribed to.
     */
    fun stop()
}
