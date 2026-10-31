// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// Responsible for managing event listeners that emit out-of-the-box logs in response to
/// various system notifications and events.
@objc
public protocol EventsListenerTarget {
    /// Starts the event listener, giving it an opportunity to subscribe to notifications
    /// and system events.
    func start()

    /// Stops the event listener, giving it an opportunity to unsubscribe from notifications
    /// and system events it previously subscribed to.
    func stop()
}
