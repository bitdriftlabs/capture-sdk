// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit
import Darwin

struct DeviceMetadata {
    let manufacturer: String
    let model: String
    let osVersion: String
    let osBrand: String
    
    static func current() -> DeviceMetadata {
        let device = UIDevice.current
        return DeviceMetadata(
            manufacturer: "Apple",
            model: DeviceMetadata.getDeviceModel(),
            osVersion: device.systemVersion,
            osBrand: "iOS"
        )
    }
    
    private static func getDeviceModel() -> String {
        var size = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        var machine = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &machine, &size, nil, 0)
        return String(cString: machine)
    }
}

struct AppMetadata {
    let id: String
    let version: String
    let versionCode: String
    
    static func current() -> AppMetadata {
        let bundle = Bundle.main
        return AppMetadata(
            id: bundle.bundleIdentifier ?? "unknown",
            version: bundle.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?.?.?",
            versionCode: bundle.infoDictionary?[kCFBundleVersionKey as String] as? String ?? "?"
        )
    }
}

