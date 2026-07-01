// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Snapshot of the previous app run status.
public struct PreviousRunInfo: Equatable {
    /// The best deterministic launch-time status for the previous app run.
    public let status: PreviousRunStatus

    /// Compatibility shim for the legacy experimental API.
    ///
    /// This only returns `true` when the in-process crash reporter captured a fatal crash.
    /// Other non-clean outcomes, such as `.unknown`, intentionally return `false`.
    public var hasFatallyTerminated: Bool {
        return self.status == .fatalCrash
    }
    
    /// Whether the previous run definitely terminated cleanly.
    public var wasCleanExit: Bool {
        self.status == .cleanExit
    }

    public init(
        status: PreviousRunStatus
    ) {
        self.status = status
    }

    static let unknown = PreviousRunInfo(status: .unknown)
}

/// Deterministic launch-time status for the previous app run.
public enum PreviousRunStatus: String, Equatable {
    case cleanExit
    case fatalCrash
    case appUpdate
    case osUpdate
    case debuggerAttached
    case unknown
}
