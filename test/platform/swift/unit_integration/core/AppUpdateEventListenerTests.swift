// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import XCTest

final class AppUpdateEventListenerTests: XCTestCase {
    func testEmitsAppUpdate() {
        let logger = MockCoreLogging()
        let timeProvider = MockTimeProvider()

        let noLogExpectation = self.expectation(description: "log app update is not called")
        noLogExpectation.isInverted = true
        logger.logAppUpdateExpectation = noLogExpectation
        logger.shouldLogAppUpdateEvent = false

        let listener = AppUpdateEventListener(
            logger: logger,
            clientAttributes: ClientAttributes(),
            timeProvider: timeProvider
        )
        listener.start()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [noLogExpectation], timeout: 0.5))
        XCTAssertEqual(0, logger.logAppUpdateCount)

        let logExpectation = self.expectation(description: "log app update is called")
        logger.logAppUpdateExpectation = logExpectation
        logger.shouldLogAppUpdateEvent = true

        listener.start()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [logExpectation], timeout: 0.5))
        XCTAssertEqual(1, logger.logAppUpdateCount)
    }
}
