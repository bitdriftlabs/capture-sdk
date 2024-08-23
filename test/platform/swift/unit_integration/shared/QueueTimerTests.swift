// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class QueueTimerTests: XCTestCase {
    private var timer: QueueTimer!

    override func tearDown() {
        super.tearDown()

        self.timer.invalidate()
    }

    func testTimerFiresImmediatelyUponSubscription() {
        let expectation = self.expectation(description: "Timer fires immediately upon scheduling")

        self.timer = QueueTimer.scheduledTimer(
            withTimeInterval: 10,
            queue: .main
        ) {
            dispatchPrecondition(condition: .onQueue(.main))
            expectation.fulfill()
        }

        XCTAssertEqual(.completed, XCTWaiter.wait(for: [expectation], timeout: 0.5))
    }

    func testTimerFiresRepetitively() {
        let expectation = self.expectation(description: "Timer fires repetetively")
        expectation.expectedFulfillmentCount = 2

        self.timer = QueueTimer.scheduledTimer(
            withTimeInterval: 0.35,
            queue: .main
        ) {
            dispatchPrecondition(condition: .onQueue(.main))
            expectation.fulfill()
        }

        XCTAssertEqual(.completed, XCTWaiter.wait(for: [expectation], timeout: 0.5))
    }
}
