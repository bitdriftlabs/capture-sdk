// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import Capture
import MetricKit
import SwiftUI

final class CrashPanelViewModel: NSObject, ObservableObject {
    @Published private(set) var recentCrashDiagnostics: [StoredCrashDiagnostic] = []
    @Published private(set) var crashEnvironment: CrashEnvironmentSnapshot

    private let captureConfiguration = Capture.Configuration()
    private let crashDiagnosticsStore: CrashDiagnosticsStore
    
    init(diagnosticsStore: CrashDiagnosticsStore = .init()) {
        self.crashDiagnosticsStore = diagnosticsStore
        self.crashEnvironment = CrashEnvironmentSnapshot.capture(
            fatalIssueReportingEnabled: self.captureConfiguration.enableFatalIssueReporting
        )
        super.init()
        self.crashDiagnosticsStore.clear()
        MXMetricManager.shared.add(self)
    }
}

// MARK: - MXMetricManagerSubscriber related methods
extension CrashPanelViewModel: MXMetricManagerSubscriber {
    func didReceive(_ payloads: [MXMetricPayload]) {
        Capture.Logger.logDebug("Did Receive MXMetricPayload")
        for payload in payloads {
            Capture.Logger.logDebug(
                "Did receive MXMetricPayload",
                fields: ["payload": String(data: payload.jsonRepresentation(), encoding: .utf8)]
            )
        }
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        var crashDiagnostics: [StoredCrashDiagnostic] = []
        Capture.Logger.logDebug("Did Receive MXDiagnosticPayload")
        for payload in payloads {
            Capture.Logger.logDebug(
                "Did Receive MXDiagnosticPayload",
                fields: ["payload": String(data: payload.jsonRepresentation(), encoding: .utf8)]
            )

            let payloadCrashDiagnostics = (payload.crashDiagnostics ?? []).map {
                StoredCrashDiagnostic(
                    receivedAt: Date(),
                    metricKitTimestampEnd: payload.timeStampEnd,
                    signalNumber: $0.signal?.intValue,
                    signalName: Self.signalName(for: $0.signal?.intValue),
                    exceptionType: $0.exceptionType?.intValue,
                    exceptionCode: $0.exceptionCode?.intValue,
                    terminationReason: $0.terminationReason,
                    callStackTree: String(data: $0.callStackTree.jsonRepresentation(), encoding: .utf8)
                        ?? "MetricKit did not provide a UTF-8 call stack tree.",
                    rawDiagnostic: String(describing: $0.dictionaryRepresentation())
                )
            }

            crashDiagnostics.append(contentsOf: payloadCrashDiagnostics)
        }

        let updatedDiagnostics = self.recentCrashDiagnostics + crashDiagnostics
        self.crashDiagnosticsStore.save(updatedDiagnostics)

        DispatchQueue.main.async {
            self.recentCrashDiagnostics = updatedDiagnostics
        }
    }

    private static func signalName(for signalNumber: Int?) -> String? {
        guard let signalNumber else {
            return nil
        }

        let signalNames: [Int: String] = [
            Int(SIGABRT): "SIGABRT",
            Int(SIGBUS): "SIGBUS",
            Int(SIGFPE): "SIGFPE",
            Int(SIGILL): "SIGILL",
            Int(SIGSEGV): "SIGSEGV",
            Int(SIGTRAP): "SIGTRAP",
        ]

        return signalNames[signalNumber]
    }
}

