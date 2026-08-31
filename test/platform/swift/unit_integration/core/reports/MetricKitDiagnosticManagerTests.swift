// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import MetricKit
import XCTest

@testable import Capture
@testable import CaptureLoggerBridge
@testable import CaptureMocks

#if compiler(>=6.4)

@available(iOS 27.0, *)
final class MetricKitDiagnosticManagerTests: XCTestCase {
    private var outputDir: URL!
    private var crashReporting: MockCrashReporting!
    private var source: MockDiagnosticSource!
    private var sut: MetricKitDiagnosticManager!

    override func setUp() {
        self.outputDir = FileManager.default.temporaryDirectory.appendingPathComponent(
            UUID().uuidString)
        self.crashReporting = MockCrashReporting()
        self.source = MockDiagnosticSource()
    }

    override func tearDown() {
        self.sut?.stop()
        try? FileManager.default.removeItem(at: self.outputDir)
    }

    func testCrashDiagnosticWritesReportAndCallsEnrichment() throws {
        let expectation = expectation(description: "completion")
        self.makeMetricKitDiagnosticManager(completionHandler: { expectation.fulfill() })

        self.source.yield(try self.loadDiagnosticReport("metrickit-ios27-crash-example.json"))
        self.source.finish()
        wait(for: [expectation], timeout: 5)

        let files = try FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertEqual(files.count, 1)
        XCTAssertTrue(files[0].contains("_crash_"), "expected a crash report filename, got \(files[0])")
    }

    func testMemoryExceptionWritesReportWithoutEnrichment() throws {
        let expectation = expectation(description: "completion")
        self.makeMetricKitDiagnosticManager(completionHandler: { expectation.fulfill() })

        self.source.yield(
            try self.loadDiagnosticReport("metrickit-ios27-memoryexception-example.json"))
        self.source.finish()
        wait(for: [expectation], timeout: 5)

        let files = try FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertEqual(files.count, 1)
        XCTAssertTrue(
            files[0].contains("_crash_"), "expected a crash report filename, got \(files[0])")
    }

    func testWatchdogTerminationIsClassifiedAsAnrNotCrash() throws {
        let expectation = expectation(description: "completion")
        self.makeMetricKitDiagnosticManager(completionHandler: { expectation.fulfill() })

        self.source.yield(try self.loadDiagnosticReport("metrickit-ios27-anr-example.json"))
        self.source.finish()
        wait(for: [expectation], timeout: 5)

        let files = try FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertEqual(files.count, 1)
        XCTAssertTrue(
            files[0].contains("_anr_"), "expected an ANR report filename, got \(files[0])")
    }

    func testCrashUsesCachedCrashDateInsteadOfReportTimeRange() throws {
        let crashDate = Date(timeIntervalSince1970: 1_700_000_000)
        self.crashReporting = MockCrashReporting(crashDate: crashDate)

        let expectation = expectation(description: "completion")
        self.makeMetricKitDiagnosticManager(completionHandler: { expectation.fulfill() })

        let report = try self.loadDiagnosticReport("metrickit-ios27-crash-example.json")
        self.source.yield(report)
        self.source.finish()
        wait(for: [expectation], timeout: 5)

        try self.assertSingleReport(timestamp: crashDate.timeIntervalSince1970, type: "crash")
    }

    func testCrashFallsBackToReportTimeRangeWhenNoCachedCrashDate() throws {
        let expectation = expectation(description: "completion")
        self.makeMetricKitDiagnosticManager(completionHandler: { expectation.fulfill() })

        let report = try self.loadDiagnosticReport("metrickit-ios27-crash-example.json")
        self.source.yield(report)
        self.source.finish()
        wait(for: [expectation], timeout: 5)

        try self.assertSingleReport(
            timestamp: report.timeRange.end.timeIntervalSince1970, type: "crash")
    }

    func testMemoryExceptionKeepsReportTimeRangeDespiteCachedCrashDate() throws {
        self.crashReporting = MockCrashReporting(
            crashDate: Date(timeIntervalSince1970: 1_700_000_000))

        let expectation = expectation(description: "completion")
        self.makeMetricKitDiagnosticManager(completionHandler: { expectation.fulfill() })

        let report = try self.loadDiagnosticReport(
            "metrickit-ios27-memoryexception-example.json")
        self.source.yield(report)
        self.source.finish()
        wait(for: [expectation], timeout: 5)

        try self.assertSingleReport(
            timestamp: report.timeRange.end.timeIntervalSince1970, type: "crash")
    }

    func testStopCancelsConsumption() throws {
        self.makeMetricKitDiagnosticManager()
        self.sut.stop()

        // Yielding after stop() shouldn't crash or write anything; the consuming Task is cancelled.
        self.source.yield(
            try self.loadDiagnosticReport("metrickit-ios27-memoryexception-example.json"))
        self.source.finish()

        let files = try? FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertTrue(files?.isEmpty ?? true)
    }
}

@available(iOS 27.0, *)
extension MetricKitDiagnosticManagerTests {
    func makeMetricKitDiagnosticManager(completionHandler: (() -> Void)? = nil) {
        self.sut = MetricKitDiagnosticManager(
            outputDir: self.outputDir,
            sdkVersion: "1.0.0",
            memoryPressureLevel: .normal,
            fileSizeOptimizationEnabled: false,
            useStackOverlapMatching: false,
            crashReporting: self.crashReporting,
            diagnosticSource: self.source,
            completionHandler: completionHandler
        )
        self.sut.start()
    }

    func assertSingleReport(timestamp: TimeInterval, type: String) throws {
        let files = try FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertEqual(files.count, 1)
        let expectedPrefix = String(format: "%.6f_%@_", timestamp.rounded(.towardZero), type)
        XCTAssertTrue(
            files[0].hasPrefix(expectedPrefix),
            "expected a report named \(expectedPrefix)*, got \(files[0])"
        )
    }

    func loadDiagnosticReport(_ resourceName: String) throws -> DiagnosticReport {
        let testBundle = Bundle(for: type(of: self))
        let url = try XCTUnwrap(testBundle.url(forResource: resourceName, withExtension: nil))
        return try JSONDecoder().decode(DiagnosticReport.self, from: Data(contentsOf: url))
    }
}

#endif
