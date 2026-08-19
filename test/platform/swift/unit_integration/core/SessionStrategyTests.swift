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

    func testSessionConfigurationUsesSeededAndSDKGeneratedIDs() throws {
        let initialSessionID = "initial-session"
        let explicitSessionID = "explicit-session"
        var observedSessionIDs = [String]()

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            sessionConfiguration: SessionConfiguration(
                initialSessionID: initialSessionID,
                onSessionIDChanged: { observedSessionIDs.append($0) }
            )
        )

        XCTAssertEqual(initialSessionID, logger.sessionID)
        let initialCallback = expectation(description: "initial callback")
        DispatchQueue.main.async {
            XCTAssertEqual([initialSessionID], observedSessionIDs)
            initialCallback.fulfill()
        }
        wait(for: [initialCallback], timeout: 1)

        logger.startNewSession(sessionID: explicitSessionID)
        XCTAssertEqual(explicitSessionID, logger.sessionID)

        logger.startNewSession()
        XCTAssertNotNil(UUID(uuidString: logger.sessionID))
    }

    func testFixedCompatibilityShimDoesNotInvokeSessionIDGenerator() throws {
        var generatorCalls = 0

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            sessionStrategy: .fixed(sessionIDGenerator: {
                generatorCalls += 1
                return "ignored"
            })
        )

        XCTAssertEqual(0, generatorCalls)
        XCTAssertNotNil(UUID(uuidString: logger.sessionID))
    }

    func testActivityBasedSessionConfiguration() throws {
        let expectation = self.expectation(description: "onSessionIDChange called")
        var observedSessionID: String?

        let logger = try Logger.testLogger(
            withAPIKey: "test_api_key",
            sessionConfiguration: SessionConfiguration(
                inactivityTimeout: 30 * 60,
                onSessionIDChanged: { sessionID in
                    dispatchPrecondition(condition: .onQueue(.main))
                    observedSessionID = sessionID
                    expectation.fulfill()
                }
            )
        )

        let sessionID = logger.sessionID

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))
        XCTAssertEqual(observedSessionID, sessionID)

        logger.startNewSession()
        let newSessionID = logger.sessionID

        XCTAssertNotEqual(sessionID, newSessionID)
    }

    func testConcurrentSessionReadsAndRotations() throws {
        let logger = try Logger.testLogger(withAPIKey: "test_api_key")

        // Both public calls enter bd_session::Strategy.state. Mixing them from several caller
        // threads exercises the iOS PlatformMutex/os_unfair_lock implementation under contention.
        DispatchQueue.concurrentPerform(iterations: 8) { worker in
            for iteration in 0 ..< 100 {
                if (worker + iteration).isMultiple(of: 2) {
                    logger.startNewSession()
                } else {
                    _ = logger.sessionID
                }
            }
        }

        XCTAssertEqual(logger.sessionID.count, UUID().uuidString.count)
    }
}
