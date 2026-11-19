// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import XCTest

final class LoggerSharedTests: XCTestCase {
    override func setUp() {
        super.setUp()
        Logger.resetShared(logger: MockLogging())
    }

    override func tearDown() {
        super.tearDown()
        Logger.resetShared()
    }

    func testGetSdkStatus_beforeStart_returnsNotStarted() {
        Logger.resetShared()

        let status = Capture.Logger.getSdkStatus()

        XCTAssertEqual(status.initializationState, InitializationState.notStarted)
        XCTAssertNil(status.lastHandshakeTime)
        XCTAssertNil(status.lastConfigDeliveryTime)
    }

    func testGetSdkStatus_afterStart_returnsNonNotStartedState() {
        Logger.resetShared()
        self.startLoggerWithIsolatedDirectory(apiKey: "test_api_key")

        let status = Capture.Logger.getSdkStatus()

        XCTAssertNotEqual(status.initializationState, InitializationState.notStarted)
    }

    func testClearEntityID_usesSharedLogger() throws {
        let logger = try XCTUnwrap(Capture.Logger.getShared() as? MockLogging)

        Capture.Logger.clearEntityID()

        XCTAssertEqual(1, logger.clearEntityIDCallCount)
    }

    func testIntegrationsAreEnabledOnlyOnce() throws {
        var integrationStartsCount = 0
        let integration = Integration { _, _, _ in
            integrationStartsCount += 1
        }

        let integrator = try XCTUnwrap(
            self.startLoggerWithIsolatedDirectory(apiKey: "foo")
        )

        integrator.enableIntegrations([integration])
        XCTAssertEqual(1, integrationStartsCount)
        integrator.enableIntegrations([integration])
        XCTAssertEqual(1, integrationStartsCount)
    }
}
