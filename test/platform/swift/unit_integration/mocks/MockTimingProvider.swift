// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation

final class MockTimeProvider {
    var timeIntervalSince1970: TimeInterval

    init(timeIntervalSince1970: TimeInterval = Date().timeIntervalSince1970) {
        self.timeIntervalSince1970 = timeIntervalSince1970
    }

    func advanceBy(timeInterval: TimeInterval) {
        self.timeIntervalSince1970 += timeInterval
    }
}

extension MockTimeProvider: TimeProvider {
    func uptime() -> Uptime { Uptime(uptime: self.timeIntervalSince1970) }

    func now() -> Date { Date(timeIntervalSince1970: self.timeIntervalSince1970) }
}
