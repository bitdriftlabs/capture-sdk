// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import UIKit

/// Attributes related to app state.
final class AppStateAttributes {
    /// Whether the app is in the foreground.
    var isForeground: Bool { self.underlyingIsForeground.load() }

    private let underlyingIsForeground: Atomic<Bool>
    private weak var logger: CoreLogging?
    private var notificationTokens: [NSObjectProtocol] = []

    init(notificationCenter: NotificationCenter = .default) {
        let appState = if Thread.isMainThread {
            UIApplication.shared.applicationState
        } else {
            DispatchQueue.main.sync {
                UIApplication.shared.applicationState
            }
        }

        self.underlyingIsForeground = Atomic(appState != .background)

        let appForegrounded = { [weak self] (_: Notification) in
            self?.updateForeground(true)
            return
        }

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
                self?.updateForeground(false)
            },
        ]
    }

    func start(with logger: CoreLogging) {
        self.logger = logger
    }

    func initialOotbFields() -> [Field] {
        [
            Field(
                key: "foreground",
                data: (self.isForeground ? "1" : "0") as NSString,
                type: .string
            ),
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

private extension AppStateAttributes {
    func updateForeground(_ isForeground: Bool) {
        self.underlyingIsForeground.update { $0 = isForeground }
        self.logger?.updateOotbField(withKey: "foreground", value: isForeground ? "1" : "0")
    }
}
