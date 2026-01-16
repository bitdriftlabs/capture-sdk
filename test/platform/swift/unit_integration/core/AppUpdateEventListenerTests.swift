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

        let listener = AppUpdateEventListener(
            logger: logger,
            clientAttributes: ClientAttributes(),
            timeProvider: timeProvider
        )

        // When shouldLogAppUpdateEvent is false, no log should be emitted
        logger.shouldLogAppUpdateEvent = false

        let noLogExpectation = self.expectation(description: "first call completes")
        listener.maybeLogAppUpdateEvent(version: "1.0", buildNumber: "1") {
            noLogExpectation.fulfill()
        }
        wait(for: [noLogExpectation], timeout: 5.0)
        XCTAssertEqual(0, logger.logAppUpdateCount)

        // When shouldLogAppUpdateEvent is true, log should be emitted
        logger.shouldLogAppUpdateEvent = true

        let logExpectation = self.expectation(description: "second call completes")
        listener.maybeLogAppUpdateEvent(version: "1.0", buildNumber: "1") {
            logExpectation.fulfill()
        }
        wait(for: [logExpectation], timeout: 5.0)
        XCTAssertEqual(1, logger.logAppUpdateCount)
    }
}
