// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Returns a high-resolution measurement of system uptime, that continues ticking through device sleep
/// *and* user- or system-generated clock adjustments. This allows for stable differences to be calculated
/// between timestamps.
protocol TimeProvider {
    func now() -> Date

    func uptime() -> Uptime
}

extension TimeProvider {
    func timeIntervalSince(_ uptime: Uptime) -> TimeInterval {
        self.uptime().timeIntervalSince(uptime)
    }
}
