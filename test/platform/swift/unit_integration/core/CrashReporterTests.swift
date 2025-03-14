// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt 

@testable
@_spi(BitdriftExperimental)
import Capture
import CaptureMocks
import CapturePassable
import Foundation
import XCTest

final class CrashReporterTests: XCTestCase {
    let reportDir = try! FileManager.default
        .url(
            for: .documentDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: false
        )
        .appendingPathComponent("bitdrift_capture")
        .appendingPathComponent("reports")

    override func setUp() {
        if FileManager.default.fileExists(atPath: reportDir.path) {
            try! FileManager.default.removeItem(at: reportDir)
        }
        try! FileManager.default.createDirectory(
            at: reportDir,
            withIntermediateDirectories: true)
        Logger.crashReporterInitResult = (.notInitialized, 0)
    }

    override func tearDown() {
        if FileManager.default.fileExists(atPath: reportDir.path) {
            try! FileManager.default.removeItem(at: reportDir)
        }
    }

    func testNoConfig() {
        Logger.initCrashReporting()
        let result = Logger.crashReporterInitResult
        XCTAssertEqual(CrashReporterInitState.initialized(.missingConfigFile), result.0)

        let (reporterState, reporterDuration) = startAndExpectLog()
        XCTAssertEqual("MISSING_CRASH_CONFIG_FILE", reporterState)
        XCTAssertGreaterThan(reporterDuration, 0)
    }

    func testNoConfigDir() {
        try! FileManager.default.removeItem(at: reportDir)
        Logger.initCrashReporting()
        let result = Logger.crashReporterInitResult
        XCTAssertEqual(CrashReporterInitState.initialized(.missingConfigFile), result.0)

        let (reporterState, reporterDuration) = startAndExpectLog()
        XCTAssertEqual("MISSING_CRASH_CONFIG_FILE", reporterState)
        XCTAssertGreaterThan(reporterDuration, 0)
    }

    func testBrokenConfig() {
        createConfig("some nonsense")
        Logger.initCrashReporting()
        let result = Logger.crashReporterInitResult
        XCTAssertEqual(CrashReporterInitState.initialized(.malformedConfigFile), result.0)

        let (reporterState, reporterDuration) = startAndExpectLog()
        XCTAssertEqual("MALFORMED_CRASH_CONFIG_FILE", reporterState)
        XCTAssertGreaterThan(reporterDuration, 0)
    }

    func testNoMatchingFiles() {
        createConfig("the-files/special,yaml")
        createInCache(directory: "the-files/special", filename: "something.yam", contents: "<stuff>")
        Logger.initCrashReporting()
        let result = Logger.crashReporterInitResult
        XCTAssertEqual(CrashReporterInitState.initialized(.withoutPriorCrash), result.0)

        let (reporterState, reporterDuration) = startAndExpectLog()
        XCTAssertEqual("NO_PRIOR_CRASHES", reporterState)
        XCTAssertGreaterThan(reporterDuration, 0)
    }

    func testMatchingFile() {
        createConfig("the-files/more-special,json")
        let modDate = Date() - TimeInterval(150)
        createInCache(directory: "the-files/more-special",
                      filename: "something.json",
                      contents: "<stuff>",
                      attributes: [.modificationDate: modDate])
        Logger.initCrashReporting()
        let result = Logger.crashReporterInitResult
        XCTAssertEqual(CrashReporterInitState.initialized(.sent), result.0)
        XCTAssertLessThan(result.1, 50)

        let destination = reportDir.appendingPathComponent("new", isDirectory: true)
        let files = try! FileManager.default.contentsOfDirectory(atPath: destination.path)
        XCTAssertEqual(1, files.count)

        let nameinfo = files[0].split(separator: "_")
        let timestamp = UInt64(nameinfo[0])!
        XCTAssertEqual(UInt64(modDate.timeIntervalSince1970 * 1_000), timestamp)
        XCTAssertEqual("something.json", nameinfo[1])

        let contents = FileManager.default.contents(atPath: destination.appendingPathComponent(files[0]).path)!
        XCTAssertEqual("<stuff>", String(data: contents, encoding: .utf8))

        let (reporterState, reporterDuration) = startAndExpectLog()
        XCTAssertEqual("CRASH_REPORT_SENT", reporterState)
        XCTAssertGreaterThan(reporterDuration, 0)
    }

    // MARK: - Logging

    /// Create a logger and retrieve the reporter-related fields of the SDK start log (state, duration)
    func startAndExpectLog() -> (String, TimeInterval) {
        let bridge = MockLoggerBridging()

        let _ = try! Logger.testLogger(
            withAPIKey: "test_api_key",
            bufferDirectory: Logger.tempBufferDirectory(),
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )

        let log = bridge.startLog.load()!
        let state = (log.0).filter { field in
            return field.key == "_crash_reporting_state"
        }.first!

        let duration = (log.0).filter { field in
            return field.key == "_crash_reporting_duration_ms"
        }.first!
        return (state.data as! String, Double(duration.data as! String)!)
    }

    // MARK: - File utilities

    func createInCache(directory: String, filename: String, contents: String,
                       attributes: [FileAttributeKey: Any]? = nil) {
        let cacheDir = try! FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: false)
        let destDir = cacheDir.appendingPathComponent(directory)
        try! FileManager.default.createDirectory(
            at: destDir,
            withIntermediateDirectories: true)
        createFile(location: destDir.appendingPathComponent(filename),
                   contents: contents, attributes: attributes)
    }

    func createConfig(_ contents: String) {
        let configURL = reportDir.appendingPathComponent("directories", isDirectory: false)
        createFile(
            location: configURL,
            contents: contents)
    }

    func createFile(location: URL, contents: String,
                    attributes: [FileAttributeKey: Any]? = nil) {
        XCTAssert(FileManager.default.createFile(
            atPath: location.path,
            contents: contents.data(using: .utf8),
            attributes: attributes
        ))
    }
}
