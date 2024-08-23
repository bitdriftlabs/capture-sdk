// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
import Foundation

// A marker for types that can be loaded from the runtime configuration.
protocol RuntimeValue {}

extension Bool: RuntimeValue {}
extension UInt32: RuntimeValue {}

/// The runtime variable.
struct RuntimeVariable<T: RuntimeValue> {
    /// The name of the runtime value, used to retrieve the relevant entry from the configuration coming from
    /// the remote control plane.
    let name: String
    /// The default value to use when the relevant configuration entry is missing.
    let defaultValue: T
}

extension RuntimeVariable {
    func load(loggerID: LoggerID) -> T {
        if T.self == Bool.self {
            // swiftlint:disable:next force_cast
            capture_runtime_bool_variable_value(loggerID, self.name, self.defaultValue as! Bool) as! T
        } else if T.self == UInt32.self {
            // swiftlint:disable:next force_cast
            capture_runtime_uint32_variable_value(loggerID, self.name, self.defaultValue as! UInt32) as! T
        } else {
            fatalError("unsupported runtime variable type")
        }
    }
}

/// A feature that can be tracked via the runtime configuration system.
extension RuntimeVariable<Bool> {
    static let sessionReplay = RuntimeVariable(
        name: "client_features.ios.session_replay",
        defaultValue: true
    )

    static let periodicLowPowerModeReporting = RuntimeVariable(
        name: "client_features.ios.resource_reporting.low_power",
        defaultValue: true
    )

    static let memoryStateChangeReporting = RuntimeVariable(
        name: "client_features.ios.memory_state_change_reporting",
        defaultValue: true
    )

    static let deviceLifecycleReporting = RuntimeVariable(
        name: "client_features.ios.device_lifecycle_reporting",
        defaultValue: true
    )

    static let applicationUpdatesReporting = RuntimeVariable(
        name: "client_feature.ios.application_update_reporting",
        defaultValue: true
    )

    static let applicationANRReporting = RuntimeVariable(
        name: "client_feature.ios.application_anr_reporting",
        // TODO(Augustyniak): Flip default to `true` once we verify that the feature doesn't cause any issues.
        defaultValue: false
    )

    static let applicationLifecycleReporting = RuntimeVariable(
        name: "client_features.ios.application_lifecycle_reporting",
        defaultValue: true
    )

    static let applicationExitReporting = RuntimeVariable(
        name: "client_feature.ios.application_exit_reporting",
        defaultValue: true
    )

    static let diskUsageReporting = RuntimeVariable(
        name: "client_feature.ios.disk_usage_reporting",
        defaultValue: true
    )

    static let internalLogs = RuntimeVariable(
        name: "client_feature.ios.internal_logs",
        defaultValue: false
    )

    static let loggerFlushingOnForceQuit = RuntimeVariable(
        name: "client_feature.ios.logger_flushing_on_force_quit",
        defaultValue: true
    )
}

extension RuntimeVariable<UInt32> {
    static let applicationANRReporterThresholdMs = RuntimeVariable(
        name: "client_feature.ios.application_anr_reporting.threshold_ms",
        defaultValue: 2_000
    )
}
