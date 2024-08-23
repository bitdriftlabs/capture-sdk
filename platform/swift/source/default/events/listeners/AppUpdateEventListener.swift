// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Responsible for an out-of-the-box app update event.
final class AppUpdateEventListener {
    private let logger: CoreLogging
    private let clientAttributes: ClientAttributes
    private let timeProvider: TimeProvider

    private let queue: DispatchQueue = .heavy

    init(logger: CoreLogging, clientAttributes: ClientAttributes, timeProvider: TimeProvider) {
        self.logger = logger
        self.clientAttributes = clientAttributes
        self.timeProvider = timeProvider
    }

    /// Logs an OOTB app update event. The event is logged only once for any given app release.
    ///
    /// - parameter version:     The app version.
    /// - parameter buildNumber: The build number.
    func maybeLogAppUpdateEvent(version: String, buildNumber: String) {
        self.queue.async { [weak self] in
            guard let start = self?.timeProvider.uptime() else {
                return
            }

            guard self?.logger.runtimeValue(.applicationUpdatesReporting) == true else {
                return
            }

            guard self?.logger.shouldLogAppUpdate(appVersion: version, buildNumber: buildNumber) == true else
            {
                return
            }

            do {
                let size = try FileManager.default.allocatedSizeOfDirectory(at: Bundle.main.bundleURL)
                if let duration = self?.timeProvider.timeIntervalSince(start) {
                    self?.logger.logAppUpdate(
                        appVersion: version,
                        buildNumber: buildNumber,
                        appSizeBytes: size,
                        duration: duration
                    )
                }
            } catch let error {
                self?.logger.handleError(context: "AppUpdateLogger", error: error)
            }
        }
    }
}

extension AppUpdateEventListener: EventListener {
    func start() {
        self.maybeLogAppUpdateEvent(
            version: self.clientAttributes.appVersion,
            buildNumber: self.clientAttributes.buildNumber
        )
    }

    func stop() {}
}
