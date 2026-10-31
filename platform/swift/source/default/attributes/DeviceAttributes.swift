// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// Attributes related to device information that is read once during logger initialization.
final class DeviceAttributes {
    let hardwareVersion = DeviceAttributes.hardwareVersion()

    private static func hardwareVersion() -> String {
        let size = UnsafeMutablePointer<Int>.allocate(capacity: 1)
        sysctlbyname("hw.machine", nil, size, nil, 0)

        var machine = [CChar](repeating: 0, count: size.pointee)
        sysctlbyname("hw.machine", &machine, size, nil, 0)
        size.deallocate()

        return String(cString: machine)
    }
}
