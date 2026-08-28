// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import Foundation

/// Provides the locale snapshot and forwards locale changes to the native OOTB field store.
final class LocaleAttributes {
    private let locale = Atomic<String>(Locale.current.identifier)
    private weak var logger: CoreLogging?
    private var notificationRegistrationToken: NSObjectProtocol?

    func initialOotbFields() -> [Field] {
        [
            Field(key: "_locale", data: self.locale.load() as NSString, type: .string),
        ]
    }

    /// Starts forwarding locale changes. The initial locale is synchronously installed when the
    /// logger is created, so starting this listener does not need to replay it.
    ///
    /// - parameter logger: The logger that receives locale changes.
    func start(with logger: CoreLogging) {
        guard self.logger == nil else {
            return
        }

        self.logger = logger
        self.notificationRegistrationToken = NotificationCenter.default.bitdrift_addObserver(
            forName: NSLocale.currentLocaleDidChangeNotification
        ) { [weak self] _ in
            self?.updateLocale()
        }
    }

    deinit {
        if let token = self.notificationRegistrationToken {
            NotificationCenter.default.removeObserver(token)
        }
    }

    private func updateLocale() {
        self.locale.update { $0 = Locale.current.identifier }
        self.logger?.updateOotbField(withKey: "_locale", value: self.locale.load())
    }
}
