// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

public class NotificationCenterMock: NotificationCenter, @unchecked Sendable {
    public var observedNames: [NSNotification.Name] = []

    override public func addObserver(
        forName name: NSNotification.Name?,
        object: Any?,
        queue: OperationQueue?,
        using block: @escaping (Notification) -> Void
    ) -> NSObjectProtocol {
        if let name {
            observedNames.append(name)
        }
        return super.addObserver(forName: name, object: object, queue: queue, using: block)
    }

    public var removedObserversCalledCount: Int = 0
    override public func removeObserver(_ observer: Any) {
        removedObserversCalledCount += 1
        super.removeObserver(observer)
    }
}
