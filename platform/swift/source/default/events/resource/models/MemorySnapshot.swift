// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

struct MemorySnapshot {
    /// Total limit of memory that the OS will allow this app to use before terminating it.
    /// Includes the app's current usage.
    let appTotalMemoryLimitKB: UInt64
    /// Amount of memory being consumed by the app.
    let appTotalMemoryUsedKB: UInt64
    /// Total amount of memory on the device.
    let deviceTotalMemoryKB: UInt64

    /// The reason that this snapshot was captured.
    var reason: Reason = .periodic

    /// Amount of time, in seconds, since the last snapshot.
    let relativeTimestamp: TimeInterval?
    /// Amount of time that has elapsed since the device booted, in seconds.
    let timeSinceDeviceBoot: TimeInterval

    /// Sequence number of the snapshot since app launch.
    let sequenceNumber: Int
    /// Time it took to capture and calculate this snapshot, in microseconds.
    let timeToCaptureMicroseconds: Int

    enum Reason {
        case lowMemory
        case periodic
    }
}

extension MemorySnapshot: ResourceSnapshot {
    func toDictionary() -> [String: String] {
        return [
            "_app_limit_kb": String(self.appTotalMemoryLimitKB),
            "_app_used_kb": String(self.appTotalMemoryUsedKB),
            "_device_kb": String(self.deviceTotalMemoryKB),
            "_since_boot_s": String(self.timeSinceDeviceBoot),
            "_sequence": String(self.sequenceNumber),
            "_capture_us": String(self.timeToCaptureMicroseconds),
        ]
    }
}
