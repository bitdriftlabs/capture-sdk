// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class SessionStrategyTests: XCTestCase {
    override func setUp() {
        super.setUp()
        Storage.shared.clear()
    }

    func testFixedSessionStrategy() throws {
        var generatedSessionIDs = [String]()

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            sessionStrategy: SessionStrategy.fixed {
                let sessionID = UUID().uuidString
                generatedSessionIDs.append(sessionID)
                return sessionID
            }
        )

        let sessionID = logger.sessionID

        XCTAssertEqual(1, generatedSessionIDs.count)
        XCTAssertEqual(sessionID, generatedSessionIDs[0])

        logger.startNewSession()

        XCTAssertEqual(2, generatedSessionIDs.count)
        XCTAssertEqual(logger.sessionID, generatedSessionIDs[1])
    }

    func testActivityBasedSessionStrategy() throws {
        let expectation = self.expectation(description: "onSessionIDChange called")
        var observedSessionID: String?

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            sessionStrategy: SessionStrategy.activityBased { sessionID in
                dispatchPrecondition(condition: .onQueue(.main))
                observedSessionID = sessionID
                expectation.fulfill()
            }
        )

        let sessionID = logger.sessionID

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))
        XCTAssertEqual(observedSessionID, sessionID)

        logger.startNewSession()
        let newSessionID = logger.sessionID

        XCTAssertNotEqual(sessionID, newSessionID)
    }
}
