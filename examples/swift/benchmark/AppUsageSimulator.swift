// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

// Simulates app activity. Useful for the purpose of running benchmarking tests.
final class AppUsageSimulator {
    private var currentWorkItem: DispatchWorkItem?

    func start() {
        // The app starts in foreground/active state so start by simulating app close first.
        self.scheduleAppCloseNotifications()
    }

    func stop() {
        self.currentWorkItem?.cancel()
        self.currentWorkItem = nil
    }

    private func scheduleAppCloseNotifications() {
        let workItem = DispatchWorkItem { [weak self] in
            NotificationCenter.default.post(name: UIScene.willDeactivateNotification, object: nil)
            NotificationCenter.default.post(name: UIScene.didEnterBackgroundNotification, object: nil)
            self?.scheduleAppOpenNotifications()
        }

        self.currentWorkItem = workItem
        // App stays in the foreground for 30s and moves to background.
        DispatchQueue.main.asyncAfter(deadline: DispatchTime.now() + 30, execute: workItem)
    }

    private func scheduleAppOpenNotifications() {
        let workItem = DispatchWorkItem { [weak self] in
            NotificationCenter.default.post(name: UIScene.willEnterForegroundNotification, object: nil)
            NotificationCenter.default.post(name: UIScene.didActivateNotification, object: nil)
            self?.scheduleAppCloseNotifications()
        }

        self.currentWorkItem = workItem
        // App stays in the background for 10s and moves to foreground.
        DispatchQueue.main.asyncAfter(deadline: DispatchTime.now() + 10, execute: workItem)
    }
}
