// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import MetricKit

#if compiler(>=6.4)

@available(iOS 27.0, *)
public final class MockDiagnosticSource: MetricDiagnosticReportSource {
    public let diagnosticReports: any AsyncSequence<DiagnosticReport, Never>
    private let continuation: AsyncStream<DiagnosticReport>.Continuation

    public init() {
        var continuation: AsyncStream<DiagnosticReport>.Continuation!
        self.diagnosticReports = AsyncStream<DiagnosticReport> { continuation = $0 }
        self.continuation = continuation
    }

    public func yield(_ report: DiagnosticReport) {
        self.continuation.yield(report)
    }

    public func finish() {
        self.continuation.finish()
    }
}

#endif
