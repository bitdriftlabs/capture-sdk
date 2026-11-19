// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct LowPowerStateSnapshot {
    /// Whether low power mode is enabled.
    let lowPowerModeEnabled: Bool
}

extension LowPowerStateSnapshot: ResourceSnapshot {
    func toDictionary() -> [String: String] {
        return [
            "_low_power_enabled": self.lowPowerModeEnabled ? "1" : "0",
        ]
    }
}
