// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct StoredCrashDiagnostic: Codable, Identifiable {
    let id: UUID
    let receivedAt: Date
    let metricKitTimestampEnd: Date
    let signalNumber: Int?
    let signalName: String?
    let exceptionType: Int?
    let exceptionCode: Int?
    let terminationReason: String?
    let callStackTree: String
    let rawDiagnostic: String
    let summary: String

    init(
        id: UUID = UUID(),
        receivedAt: Date,
        metricKitTimestampEnd: Date,
        signalNumber: Int?,
        signalName: String?,
        exceptionType: Int?,
        exceptionCode: Int?,
        terminationReason: String?,
        callStackTree: String,
        rawDiagnostic: String
    ) {
        self.id = id
        self.receivedAt = receivedAt
        self.metricKitTimestampEnd = metricKitTimestampEnd
        self.signalNumber = signalNumber
        self.signalName = signalName
        self.exceptionType = exceptionType
        self.exceptionCode = exceptionCode
        self.terminationReason = terminationReason
        self.callStackTree = callStackTree
        self.rawDiagnostic = rawDiagnostic

        if let terminationReason, !terminationReason.isEmpty {
            self.summary = terminationReason
        } else if let signalName {
            self.summary = signalName
        } else if let exceptionType {
            self.summary = "Exception \(exceptionType)"
        } else {
            self.summary = "Crash diagnostic"
        }
    }

    func exceptionDescription(type: Int) -> String {
        if let exceptionCode {
            return "type \(type), code \(exceptionCode)"
        }

        return "type \(type)"
    }
}
