// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit

/// Attributes related to app state.
final class AppStateAttributes {
    /// Whether the app is in the foreground.
    var isForeground: Bool { self.underlyingIsForeground.load() }

    private let underlyingIsForeground: Atomic<Bool>
    private var notificationTokens: [NSObjectProtocol] = []

    init() {
        let state = if Thread.isMainThread {
            UIApplication.shared.applicationState
        } else {
            DispatchQueue.main.sync {
                // The a UIKit API needs to be accessed on the main thread/queue.
                UIApplication.shared.applicationState
            }
        }

        self.underlyingIsForeground = Atomic(state != .background)

        let appForegrounded = { [weak self] (_: Notification) -> Void in
            self?.underlyingIsForeground.update { $0 = true }
        }

        let notificationCenter = NotificationCenter.default
        self.notificationTokens = [
            notificationCenter.bitdrift_addObserver(
                forName: UIApplication.willEnterForegroundNotification,
                using: appForegrounded
            ),
            notificationCenter.bitdrift_addObserver(
                forName: UIApplication.didBecomeActiveNotification,
                using: appForegrounded
            ),
            notificationCenter.bitdrift_addObserver(
                forName: UIApplication.didEnterBackgroundNotification
            ) { [weak self] _ in
                self?.underlyingIsForeground.update { $0 = false }
            },
        ]
    }

    deinit {
        self.notificationTokens.forEach(NotificationCenter.default.removeObserver)
    }
}

extension AppStateAttributes: FieldProvider {
    func getFields() -> Fields {
        return [
            /// Whether or not the app was in the background by the time the log was fired.
            "foreground": self.isForeground ? "1" : "0",
        ]
    }
}
