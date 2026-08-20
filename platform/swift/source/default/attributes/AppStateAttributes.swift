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
    private let notificationCenter: NotificationCenter
    private weak var logger: CoreLogging?
    private var notificationTokens: [NSObjectProtocol] = []

    init(notificationCenter: NotificationCenter = .default) {
        self.notificationCenter = notificationCenter

        let state = if Thread.isMainThread {
            UIApplication.shared.applicationState
        } else {
            DispatchQueue.main.sync {
                // The UIKit API needs to be accessed on the main thread/queue.
                UIApplication.shared.applicationState
            }
        }

        self.underlyingIsForeground = Atomic(state != .background)

        let appForegrounded = { [weak self] (_: Notification) in
            _ = self?.updateForeground(true)
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

    /// Starts forwarding future foreground-state changes to the logger.
    ///
    /// The initial OOTB snapshot is not reconciled with lifecycle transitions during logger
    /// construction. Avoiding that coordination keeps initialization independent of the main thread.
    ///
    /// - parameter logger: The logger that receives future foreground-state changes.
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
        self.notificationTokens.forEach(self.notificationCenter.removeObserver)
    }
}

private extension AppStateAttributes {
    func updateForeground(_ isForeground: Bool) {
        self.underlyingIsForeground.update { $0 = isForeground }
        self.logger?.updateOotbField(withKey: "foreground", value: isForeground ? "1" : "0")
    }
}
