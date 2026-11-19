// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// Listener that subscribes to the Device State notifications and reports changes to be logged.
final class DeviceStateListener: EventListener {
    private enum Key: String {
        case lowPowerMode = "_low_power_mode"
        case orientation = "_orientation"
        case timeZone = "_time_zone"
        case thermalState = "_thermal_state"
    }

    private enum Message: String {
        case batteryStateChangeMessage = "BatteryStateChange"
        case lowPowerModeChangeMessage = "BatteryLowPowerMode"
        case orientationChangeMessage = "OrientationChange"
        case timeZoneChangeMessage = "TimeZoneChange"
        case thermalStateChangeMessage = "ThermalStateChange"
    }

    private let notificationTokens: Atomic<[NSObjectProtocol]> = Atomic([])
    private let notifications: [Notification.Name]
    private let batterySnapshotProvider = BatterySnapshotProvider()

    private let logger: CoreLogging

    init(logger: CoreLogging) {
        var notifications = [Notification.Name]()
        // UIDevice must enable monitoring to send updates. We don't know if any other sources are also
        // relying on this, so we can never disable it.
        // TODO(Augustyniak): Confirm that this may be called off the main thread which happens if the logger
        // is initialized on a non-main thread.
        UIDevice.current.isBatteryMonitoringEnabled = true
        notifications.append(UIDevice.batteryStateDidChangeNotification)
        notifications.append(ProcessInfo.thermalStateDidChangeNotification)
        notifications.append(Notification.Name.NSProcessInfoPowerStateDidChange)
        notifications.append(UIDevice.orientationDidChangeNotification)
        notifications.append(Notification.Name.NSSystemTimeZoneDidChange)
        self.notifications = notifications

        self.logger = logger
    }

    deinit {
        self.stop()
    }

    // MARK: - EventListener

    func start() {
        guard self.logger.runtimeValue(.deviceLifecycleReporting) else {
            return
        }

        self.notificationTokens.update { tokens in
            guard tokens.isEmpty else {
                return
            }

            // // swiftlint:disable:next line_length
            // Per https://developer.apple.com/documentation/foundation/nsprocessinfothermalstatedidchangenotification
            // we need to access `thermalState` before registering to thermal state notifications.
            _ = ProcessInfo.processInfo.thermalState

            tokens = self.notifications.map { name in
                return NotificationCenter
                    .default
                    .bitdrift_addObserver(forName: name) { [weak self] in self?.didReceiveNotification($0) }
            }
        }
    }

    func stop() {
        self.notificationTokens.update { tokens in
            tokens.forEach { NotificationCenter.default.removeObserver($0) }
            tokens = []
        }
    }

    // MARK: - Private

    private func didReceiveNotification(_ notification: Notification) {
        // Map and format each application event differently.
        let message: Message
        let fields: [String: String]?

        switch notification.name {
        case UIDevice.batteryStateDidChangeNotification:
            message = Message.batteryStateChangeMessage
            fields = self.batterySnapshot
        case Notification.Name.NSProcessInfoPowerStateDidChange:
            let processInfo = notification.object as? ProcessInfo
            message = Message.lowPowerModeChangeMessage
            fields = [
                Key.lowPowerMode.rawValue: (processInfo?.isLowPowerModeEnabled)
                    .flatMap { String($0) } ?? "unknown",
            ].mergedOverwritingConflictingKeys(self.batterySnapshot)
        case UIDevice.orientationDidChangeNotification:
            message = Message.orientationChangeMessage
            fields = [
                Key.orientation.rawValue: UIDevice.currentOrientationString,
            ]
        case Notification.Name.NSSystemTimeZoneDidChange:
            message = Message.timeZoneChangeMessage
            fields = [
                Key.timeZone.rawValue: TimeZone.current.identifier,
            ]
        case ProcessInfo.thermalStateDidChangeNotification:
            message = Message.thermalStateChangeMessage
            fields = [Key.thermalState.rawValue: ProcessInfo.processInfo.thermalState.toString()]
        default:
            self.logger.log(
                level: .warning,
                message: "[DeviceEvent] \(notification.name.rawValue)",
                type: .device
            )
            return
        }

        self.logger.log(
            level: .info,
            message: message.rawValue,
            fields: fields,
            type: .device
        )
    }

    private var batterySnapshot: [String: String] {
        return (try? self.batterySnapshotProvider.makeSnapshot()?.toDictionary()) ?? [:]
    }
}

private extension UIDevice {
    static var currentOrientationString: String {
        let orientation = UIDevice.current.orientation
        switch orientation {
        case .unknown:
            return "unknown"
        case .portrait:
            return "portrait"
        case .portraitUpsideDown:
            return "portraitUpsideDown"
        case .landscapeLeft:
            return "landscapeLeft"
        case .landscapeRight:
            return "landscapeRight"
        case .faceUp:
            return "faceUp"
        case .faceDown:
            return "faceDown"
        default:
            return "unknown orientation: \(orientation)"
        }
    }
}

private extension ProcessInfo.ThermalState {
    func toString() -> String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal:
            "nominal"
        case .fair:
            "fair"
        case .serious:
            "serious"
        case .critical:
            "critical"
        @unknown default:
            "unknown \(ProcessInfo.processInfo.thermalState.rawValue)"
        }
    }
}
