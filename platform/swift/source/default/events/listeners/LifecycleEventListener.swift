// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// Emits out-of-the-box Capture log events in response to iOS system lifecycle notifications.
final class LifecycleEventListener {
    private enum Constants {
        // Keys
        static let sceneKey = "_scene"
        static let launchTypeKey = "_launch_type"

        // Messages
        static let applicationLaunchedMessage = "AppFinishedLaunching"
        static let applicationWillTerminateMessage = "AppWillTerminate"
        static let sceneWillConnectMessage = "SceneWillConnect"
        static let sceneDidDisconnectMessage = "SceneDidDisconnect"
        static let sceneDidActivateMessage = "SceneDidActivate"
        static let sceneWillDeactivateMessage = "SceneWillDeactivate"
        static let sceneDidEnterBackgroundMessage = "SceneDidEnterBG"
        static let sceneWillEnterForegroundMessage = "SceneWillEnterFG"
    }

    private var timeSensitiveNotificationTokens: Atomic<[NSObjectProtocol]> = Atomic([])
    private var notificationTokens: Atomic<[NSObjectProtocol]> = Atomic([])

    private let logger: CoreLogging

    init(logger: CoreLogging) {
        self.logger = logger
        self.startTimeSensitiveNotificationSubscriptions()
    }

    deinit {
        self.stop()
    }

    // MARK: - Private

    /// Starts subscription to iOS system notifications that cannot await a normal `EventListener.start()`
    /// as these notifications are emitted early in the application lifecycle and any delay in registering for
    /// them may result in the SDK not receiving them.
    private func startTimeSensitiveNotificationSubscriptions() {
        self.timeSensitiveNotificationTokens.update { tokens in
            guard tokens.isEmpty else {
                // The listener is already subscribed to notifications
                return
            }

            var notifications: [NSNotification.Name] = [UIScene.didActivateNotification]

            notifications.append(contentsOf: [UIApplication.didFinishLaunchingNotification])

            tokens = notifications.map { name in
                return NotificationCenter
                    .default
                    .bitdrift_addObserver(forName: name) { [weak self] in self?.didReceiveNotification($0) }
            }
        }
    }

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func didReceiveNotification(_ notification: Notification) {
        guard self.logger.runtimeValue(.applicationLifecycleReporting) == true else {
            return
        }

        let message: String
        var fields = [String: String]()

        if let scene = notification.object as? UIScene, let title = scene.title {
            fields[Constants.sceneKey] = title
        }

        if notification.name == UIApplication.didFinishLaunchingNotification {
            if let remoteNotificationDictionary = notification
                .userInfo?[UIApplication.LaunchOptionsKey.remoteNotification] as? [AnyHashable: Any]
            {
                fields["_rn_alert"] = remoteNotificationDictionary["alert"].flatMap(String.init(describing:))
                fields["_rn_badge"] = remoteNotificationDictionary["badge"].flatMap(String.init(describing:))
                fields["_rn_sound"] = remoteNotificationDictionary["sound"].flatMap(String.init(describing:))
                fields[Constants.launchTypeKey] = "remote_notification"
            }

            message = Constants.applicationLaunchedMessage
        } else {
            switch notification.name {
            case UIScene.willConnectNotification:
                message = Constants.sceneWillConnectMessage
            case UIScene.didDisconnectNotification:
                message = Constants.sceneDidDisconnectMessage
            case UIScene.didActivateNotification:
                message = Constants.sceneDidActivateMessage
            case UIScene.willDeactivateNotification:
                message = Constants.sceneWillDeactivateMessage
            case UIScene.didEnterBackgroundNotification:
                message = Constants.sceneDidEnterBackgroundMessage
            case UIScene.willEnterForegroundNotification:
                message = Constants.sceneWillEnterForegroundMessage
            default:
                self.logger.log(
                    level: .warning,
                    message: "Unhandled Lifecycle notification! [\(notification.name.rawValue)]",
                    error: nil,
                    type: .lifecycle
                )
                return
            }
        }

        self.logger.log(
            level: .info,
            message: message,
            fields: fields,
            error: nil,
            type: .lifecycle
        )
    }

    private func didReceiveWillTerminateNotification() {
        guard self.logger.runtimeValue(.applicationExitReporting) == true else {
            return
        }

        // Depending on what the system decides to do the `blocking` call below may or may not be able to
        // process the emitted log before the app terminates. In general, the `applicationWillTerminate(_:)`
        // method of `AppDelegate` has stronger guarantees when it comes top the amount of time it gives
        // developer to respond to app termination but we do not have access to it so need to rely on
        // `willTerminateNotification` instead.
        // swiftlint:disable:next line_length
        // https://developer.apple.com/documentation/uikit/uiapplicationdelegate/1623111-applicationwillterminate
        // for more details.
        self.logger.log(
            level: .info,
            message: "AppExit",
            fields: [
                "_app_exit_reason": "app_will_terminate_notification",
            ],
            error: nil,
            type: .lifecycle,
            blocking: true
        )
    }
}

extension LifecycleEventListener: EventListener {
    func start() {
        self.notificationTokens.update { tokens in
            guard tokens.isEmpty else {
                // The listener is already subscribed to notifications
                return
            }

            let notifications = [
                UIScene.willConnectNotification,
                UIScene.didDisconnectNotification,
                UIScene.willDeactivateNotification,
                UIScene.didEnterBackgroundNotification,
                UIScene.willEnterForegroundNotification,
            ]

            var newTokens = notifications.map { notification in
                return NotificationCenter
                    .default
                    .bitdrift_addObserver(forName: notification)
                        { [weak self] in self?.didReceiveNotification($0) }
            }

            newTokens.append(
                NotificationCenter
                    .default
                    .bitdrift_addObserver(forName: UIApplication.willTerminateNotification)
                        { [weak self] _ in self?.didReceiveWillTerminateNotification() }
            )

            tokens = newTokens
        }
    }

    func stop() {
        self.notificationTokens.update { tokens in
            for token in tokens {
                NotificationCenter.default.removeObserver(token)
            }

            tokens = []
        }

        self.notificationTokens.update { tokens in
            for token in tokens {
                NotificationCenter.default.removeObserver(token)
            }

            tokens = []
        }
    }
}
