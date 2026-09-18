// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

@objc(CAPCommandHandle)
public final class CommandHandle: NSObject {
    private let lock = Lock()
    private let unregisterHandler: () -> Void
    private var isRegistered = true

    init(unregisterHandler: @escaping () -> Void) {
        self.unregisterHandler = unregisterHandler
    }

    @objc
    public func unregister() {
        self.lock.withLock {
            guard self.isRegistered else {
                return
            }
            self.isRegistered = false
            self.unregisterHandler()
        }
    }
}
