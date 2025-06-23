// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import Foundation
import XCTest

final class ANRReporterTests: XCTestCase {
    override class func setUp() {
        super.setUp()
        Debugger.mockIsAttached(false)
        Environment.mockIsRunningTests(false)
    }

    override func tearDown() {
        super.tearDown()
        Debugger.unmock()
        Environment.unmock()
    }

    func testReportsANR() throws {
        let logger = MockCoreLogging()
        logger.mockRuntimeVariable(.applicationANRReporting, with: true)
        logger.mockRuntimeVariable(.applicationANRReporterThresholdMs, with: 500)

        let expectation = self.expectation(description: "ANR log events are logged")
        expectation.expectedFulfillmentCount = 2
        expectation.assertForOverFulfill = false
        logger.logExpectation = expectation

        let reporter = ANRReporter(logger: logger, appStateAttributes: AppStateAttributes())
        reporter.start()

        Thread.sleep(forTimeInterval: 0.7)

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 2))

        let anrLog = logger.logs[0]
        XCTAssertEqual("ANR", anrLog.message)
        XCTAssertEqual("500", try XCTUnwrap(anrLog.fields?["_threshold_duration_ms"]?.encodeToString()))

        let anrEndLog = logger.logs[1]
        XCTAssertEqual("ANREnd", anrEndLog.message)
        XCTAssertEqual("500", try XCTUnwrap(anrEndLog.fields?["_threshold_duration_ms"]?.encodeToString()))
        XCTAssert(
            try XCTUnwrap(Double(try XCTUnwrap(anrEndLog.fields?["_duration_ms"]?.encodeToString()))) > 500
        )
    }
}
