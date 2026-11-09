// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Darwin
import Foundation
internal import HelloWorldCrashSupport
import SwiftUI

struct CrashEnvironmentSnapshot {
    let isSimulator: Bool
    let isDebuggerAttached: Bool
    let isReleaseBuild: Bool
    let isFatalIssueReportingEnabled: Bool
    let runtimeCrashReportingState: CrashRuntimeCrashReportingState
    let kscrashCacheState: KSCrashCacheState

    static func capture(fatalIssueReportingEnabled: Bool) -> CrashEnvironmentSnapshot {
        CrashEnvironmentSnapshot(
            isSimulator: ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != nil,
            isDebuggerAttached: Self.isDebuggerAttached(),
            isReleaseBuild: Self.isReleaseBuild,
            isFatalIssueReportingEnabled: fatalIssueReportingEnabled,
            runtimeCrashReportingState: CrashRuntimeCrashReportingState.current(),
            kscrashCacheState: KSCrashCacheState.current()
        )
    }

    private static var isReleaseBuild: Bool {
        #if DEBUG
        false
        #else
        true
        #endif
    }

    private static func isDebuggerAttached() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout.stride(ofValue: info)
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]

        guard sysctl(&mib, UInt32(mib.count), &info, &size, nil, 0) == 0 else {
            return false
        }

        return (info.kp_proc.p_flag & P_TRACED) != 0
    }
}

enum KSCrashCacheState {
    case cached(timestamp: Date)
    case unavailable

    var badge: String {
        switch self {
        case .cached:
            return "Cached"
        case .unavailable:
            return "Unknown"
        }
    }

    var badgeColor: Color {
        switch self {
        case .cached:
            return Theme.primary
        case .unavailable:
            return Theme.secondary
        }
    }

    var message: String {
        switch self {
        case .cached(let timestamp):
            return "The Rust-side KSCrash cache is populated. Cached crash timestamp: \(Self.dateFormatter.string(from: timestamp))."
        case .unavailable:
            return "No positive cached KSCrash timestamp was found. That usually means no KSCrash report was cached for this launch, or the cached report did not expose a timestamp."
        }
    }

    static func current() -> KSCrashCacheState {
        let timestamp = hello_world_cached_kscrash_timestamp()
        guard timestamp > 0 else {
            return .unavailable
        }

        return .cached(timestamp: Date(timeIntervalSince1970: TimeInterval(timestamp)))
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .medium
        return formatter
    }()
}

/// This will try to get the actual config value from the Capture SDK.
/// If for some the way to manage internal configuration changes,
/// this wont work.
enum CrashRuntimeCrashReportingState: String {
    case enabled
    case disabled
    case missing
    case invalid

    var badge: String {
        switch self {
        case .enabled:
            return "Enabled"
        case .disabled:
            return "Disabled"
        case .missing:
            return "Unknown"
        case .invalid:
            return "Invalid"
        }
    }

    var badgeColor: Color {
        switch self {
        case .enabled:
            return Theme.primary
        case .disabled, .invalid:
            return Theme.warning
        case .missing:
            return Theme.secondary
        }
    }

    func message(clientConfigurationEnabled: Bool) -> String {
        if !clientConfigurationEnabled {
            return "The client configuration disables fatal issue reporting before runtime config is considered."
        }

        switch self {
        case .enabled:
            return "The cached runtime config currently enables `crash_reporting.enabled`."
        case .disabled:
            return "The cached runtime config currently disables `crash_reporting.enabled`."
        case .missing:
            return "No cached runtime config was found yet at Application Support/bitdrift_capture/reports/config.csv."
        case .invalid:
            return "A cached runtime config file exists, but the demo could not parse it."
        }
    }

    static func current() -> CrashRuntimeCrashReportingState {
        guard let applicationSupportDirectory = try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: false
        ) else {
            return .missing
        }

        let configURL = applicationSupportDirectory
            .appendingPathComponent("bitdrift_capture")
            .appendingPathComponent("reports/config.csv", isDirectory: false)

        guard let data = FileManager.default.contents(atPath: configURL.path),
              let contents = String(data: data, encoding: .utf8)
        else {
            return .missing
        }

        var values: [String: String] = [:]
        for line in contents.split(separator: "\n") {
            let pair = line.split(separator: ",", maxSplits: 1)
            guard pair.count == 2 else {
                return .invalid
            }

            values[String(pair[0])] = String(pair[1])
        }

        guard let rawValue = values["crash_reporting.enabled"] else {
            return .missing
        }

        switch rawValue {
        case "true":
            return .enabled
        case "false":
            return .disabled
        default:
            return .invalid
        }
    }
}
