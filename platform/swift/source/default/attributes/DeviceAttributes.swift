// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// Attributes related to the device information including hardware model, locale, etc.
final class DeviceAttributes {
    private let locale = Atomic<String>(Locale.current.identifier)
    private let hardwareVersion: String = {
        let size = UnsafeMutablePointer<Int>.allocate(capacity: 1)
        sysctlbyname("hw.machine", nil, size, nil, 0)

        var machine = [CChar](repeating: 0, count: size.pointee)
        sysctlbyname("hw.machine", &machine, size, nil, 0)
        size.deallocate()

        return String(cString: machine)
    }()

    private var notificationRegistrationToken: NSObjectProtocol?

    func start() {
        self.notificationRegistrationToken = NotificationCenter.default.bitdrift_addObserver(
            forName: NSLocale.currentLocaleDidChangeNotification
        ) { [weak self] _ in
            self?.locale.update { $0 = Locale.current.identifier }
        }
    }

    deinit {
        if let token = self.notificationRegistrationToken {
            NotificationCenter.default.removeObserver(token)
        }
    }
}

extension DeviceAttributes: FieldProvider {
    public func getFields() -> Fields {
        return [
            /// The iPhone device model (e.g. iPhone13,1)
            "model": self.hardwareVersion,
            /// The device locale (e.g. en_US)
            "_locale": self.locale.load(),
        ]
    }
}
