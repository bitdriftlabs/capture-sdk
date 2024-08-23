// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

struct BatterySnapshot {
    /// Current battery level of the device, between 0.0 and 1.0. Simulator will usually report -1.0.
    let batteryValue: Float
    /// String representation of the current `UIDevice.BatteryState`.
    let batteryState: String
}

extension BatterySnapshot: ResourceSnapshot {
    func toDictionary() -> [String: String] {
        return [
            "_battery_val": String(self.batteryValue),
            "_state": self.batteryState,
        ]
    }
}
