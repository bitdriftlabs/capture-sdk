// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

final class BatterySnapshotProvider {
    private let device: UIDevice
    init(device: UIDevice = UIDevice.current) {
        self.device = device
    }
}

extension BatterySnapshotProvider: ResourceSnapshotProvider {
    func makeSnapshot() throws -> ResourceSnapshot? {
        return BatterySnapshot(
            batteryValue: self.device.batteryLevel,
            batteryState: self.device.currentBatteryStateString
        )
    }
}

private extension UIDevice {
    var currentBatteryStateString: String {
        let state = UIDevice.current.batteryState
        switch state {
        case .unknown:
            return "unknown"
        case .unplugged:
            return "unplugged"
        case .charging:
            return "charging"
        case .full:
            return "full"
        default:
            return "unknown state \(state)"
        }
    }
}
