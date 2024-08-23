// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension NotificationCenter {
    /// Adds an entry to the notification center to receive notifications that passed to the provided block.
    ///
    /// - parameter name:    The name of the notification to subscribe to.
    /// - parameter closure: The closure to call when the notification is posted.
    ///
    /// - returns: An opaque object to act as the observer. Notification center strongly holds this return
    ///            value until you remove the observer registration.
    func bitdrift_addObserver(
        forName name: Notification.Name,
        using closure: @escaping (Notification) -> Void
    ) -> any NSObjectProtocol {
        return self.addObserver(
            forName: name,
            object: nil,
            // Passing anything other than a nil `queue` in here
            // leads to ANRs being reported by some 3rd party tools.
            // This includes `.main` or custom serial queues.
            queue: nil,
            using: closure
        )
    }
}
