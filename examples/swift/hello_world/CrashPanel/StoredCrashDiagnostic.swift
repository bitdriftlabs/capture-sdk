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

        self.summary = Self.crashName(
            signalName: signalName,
            exceptionType: exceptionType,
            terminationReason: terminationReason
        )
    }

    func exceptionDescription(type: Int) -> String {
        if let exceptionName {
            if let exceptionCode {
                return "\(exceptionName) (type \(type), code \(exceptionCode))"
            }

            return "\(exceptionName) (type \(type))"
        }

        if let exceptionCode {
            return "type \(type), code \(exceptionCode)"
        }

        return "type \(type)"
    }

    var crashName: String {
        Self.crashName(
            signalName: self.signalName,
            exceptionType: self.exceptionType,
            terminationReason: self.terminationReason
        )
    }

    var exceptionName: String? {
        Self.exceptionName(for: self.exceptionType)
    }

    private static func crashName(
        signalName: String?,
        exceptionType: Int?,
        terminationReason: String?
    ) -> String {
        if let exceptionName = Self.exceptionName(for: exceptionType) {
            return exceptionName
        }

        if let signalName {
            return signalName
        }

        if let terminationReason, !terminationReason.isEmpty {
            return terminationReason
        }

        return "Crash diagnostic"
    }

    private static func exceptionName(for exceptionType: Int?) -> String? {
        guard let exceptionType else {
            return nil
        }

        switch exceptionType {
        case Int(EXC_BAD_ACCESS):
            return "EXC_BAD_ACCESS"
        case Int(EXC_BAD_INSTRUCTION):
            return "EXC_BAD_INSTRUCTION"
        case Int(EXC_SYSCALL):
            return "EXC_SYSCALL"
        case Int(EXC_MACH_SYSCALL):
            return "EXC_MACH_SYSCALL"
        case Int(EXC_CRASH):
            return "EXC_CRASH"
        case Int(EXC_RESOURCE):
            return "EXC_RESOURCE"
        case Int(EXC_GUARD):
            return "EXC_GUARD"
        case Int(EXC_CORPSE_NOTIFY):
            return "EXC_CORPSE_NOTIFY"
        case Int(EXC_ARITHMETIC):
            return "EXC_ARITHMETIC"
        case Int(EXC_EMULATION):
            return "EXC_EMULATION"
        case Int(EXC_SOFTWARE):
            return "EXC_SOFTWARE"
        case Int(EXC_BREAKPOINT):
            return "EXC_BREAKPOINT"
        default:
            return nil
        }
    }
}
