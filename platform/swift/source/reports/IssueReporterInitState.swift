// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Progress indicator for crash reporter initialization
enum IssueReporterInitState: Equatable {
    case notInitialized
    case initializing
    case initialized(ReporterInitResolution)
}

/// Final state of an initialized crash reporter
enum ReporterInitResolution: Equatable, Error {
    /// Enabled monitoring for reports, may or may not detect any
    case monitoring
    /// Disabled due to hardware limitations
    case unsupportedHardware
    /// Core functionality disabled
    case notEnabled
    /// No directory available for delivering reports, cannot continue
    case missingReportsDirectory
    /// Crash reporting configuration file contains errors, cannot continue
    case processingFailure(String)
}

typealias IssueReporterInitResult = (IssueReporterInitState, TimeInterval)

func measureTime<T>(operation: () -> T) -> (T, TimeInterval) {
    let start = DispatchTime.now()
    let result = operation()
    let end = DispatchTime.now()
    let duration = Double(end.uptimeNanoseconds - start.uptimeNanoseconds) / Double(NSEC_PER_SEC)
    return (result, duration)
}

extension IssueReporterInitState: CustomStringConvertible {
    var description: String {
        switch self {
        case .notInitialized:
            return "NOT_INITIALIZED"
        case .initializing:
            return "INITIALIZING"
        case .initialized(let resolution):
            switch resolution {
            case .monitoring:
                return "CRASH_REPORT_MONITORING"
            case .notEnabled:
                return "CRASH_REPORT_DISABLED"
            case .unsupportedHardware:
                return "UNSUPPORTED_HARDWARE"
            case .missingReportsDirectory:
                return "MISSING_CRASH_REPORT_DIR"
            case .processingFailure(let error):
                return "CRASH_PROCESSING_FAILURE: \(error)"
            }
        }
    }
}
