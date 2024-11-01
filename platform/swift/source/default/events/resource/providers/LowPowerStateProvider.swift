// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

final class LowPowerStateProvider {
    private let isLowerPowerModeEnabled = Atomic(ProcessInfo.processInfo.isLowPowerModeEnabled)

    init() {
        // Accessing `ProcessInfo.processInfo.isLowPowerModeEnabled` frequently causes occasional crashes
        // on iOS 15 (up to at least version 15.2). To reduce these calls, subscribe to
        // `NSProcessInfoPowerStateDidChange` notifications and track the state of `isLowPowerModeEnabled` 
        // locally. This minimizes the need to call `ProcessInfo.processInfo.isLowPowerModeEnabled` here
        // more than once.
        NotificationCenter
            .default
            .addObserver(
                self,
                selector: #selector(powerStateDidChange(_:)),
                name: Notification.Name.NSProcessInfoPowerStateDidChange,
                object: nil
            )
    }

    @objc
    private func powerStateDidChange(_ userInfo: Any) {
        self.isLowerPowerModeEnabled.update { $0 = ProcessInfo.processInfo.isLowPowerModeEnabled }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}

extension LowPowerStateProvider: ResourceSnapshotProvider {
    func makeSnapshot() -> ResourceSnapshot? {
        return LowPowerStateSnapshot(lowPowerModeEnabled: self.isLowerPowerModeEnabled.load())
    }
}
