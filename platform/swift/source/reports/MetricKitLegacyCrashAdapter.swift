// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import MetricKit

#if compiler(>=6.4)

@available(iOS 27.0, *)
class MetricKitLegacyCrashAdapter {
    /// This method transforms MetricKit's v2 crash payload into the old one.
    /// The result of this method is going to be used in the rust bridge to avoid duplicating logic
    /// (and preventing any issues while doing so).
    ///
    /// - parameter diagnostic:  the information related to the crash.
    /// - parameter environment: the context where the crash happened.
    ///
    /// - returns: a dictionary in the shape of a crash reported by MetricKit in v1
    static func makeCrashDict(
        diagnostic: CrashDiagnostic,
        environment: DiagnosticReport.Environment
    ) -> [String: Any] {
        var dict = self.makeCallStackTreeDict(diagnostic.callStackTree)
        dict["diagnosticMetaData"] = [
            "signal": diagnostic.signal ?? 0,
            "pid": environment.pid.map(Int.init) ?? 0,
        ]
        return dict
    }

    /// - parameter diagnostic: the memory exception information.
    ///
    /// - returns: a dictionary in the shape of a crash reported by MetricKit in v1
    static func makeMemoryExceptionDict(diagnostic: MemoryExceptionDiagnostic) -> [String: Any]
    {
        self.makeCallStackTreeDict(diagnostic.callStackTree)
    }

    /// - parameter environment: the context where the diagnostic happened.
    ///
    /// - returns: a dictionary in the shape of `MXMetadata.dictionaryRepresentation()`.
    static func makeMetadataDict(environment: DiagnosticReport.Environment) -> [String: Any] {
        [
            "bundleIdentifier": environment.bundleIdentifier,
            "appBuildVersion": environment.applicationBuildVersion,
            "regionFormat": environment.regionFormat,
            "deviceType": environment.deviceType,
            "platformArchitecture": environment.platformArchitecture,
            "osVersion":
                "\(environment.osVersion.platform) \(environment.osVersion.number) (\(environment.osVersion.buildNumber))",
            "lowPowerModeEnabled": environment.lowPowerModeEnabled,
        ]
    }

    private static func makeCallStackTreeDict(_ tree: CallStackTree) -> [String: Any] {
        let threads = tree.callStackThreads.map { thread -> [String: Any] in
            [
                "threadAttributed": thread.threadAttributed ?? false,
                "callStackRootFrames": [self.frameDict(thread.rootFrames.first, tree: tree)]
                    .compactMap { $0 },
            ]
        }

        return [
            "callStackTree": [
                "callStacks": threads
            ],
        ]
    }

    private static func frameDict(_ frame: CallStackFrame?, tree: CallStackTree) -> [String:
        Any]?
    {
        guard
            let frame,
            let address = frame.address,
            let binaryUUID = frame.binaryUUID,
            let offset = frame.offsetIntoBinaryTextSegment
        else {
            return nil
        }

        var dict: [String: Any] = [
            "address": address,
            "binaryUUID": binaryUUID.uuidString,
            "binaryName": frame.binaryName(from: tree) ?? "",
            "offsetIntoBinaryTextSegment": offset,
        ]

        if let subFrame = self.frameDict(frame.subFrames.first, tree: tree) {
            dict["subFrames"] = [subFrame]
        } else {
            dict["subFrames"] = []
        }

        return dict
    }
}

#endif
