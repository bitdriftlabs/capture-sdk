// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
@testable import CaptureTestBridge
import XCTest

final class SessionReplayTargetTests: XCTestCase {
    private var target: Capture.SessionReplayTarget!
    private var logger: MockCoreLogging!

    override func setUp() {
        super.setUp()

        self.logger = MockCoreLogging()

        self.target = SessionReplayTarget(configuration: .init())
        self.target.logger = self.logger
    }

    func testSessionReplayTargetDoesNotCrash() {
        run_session_replay_target_test(self.target)
    }

    func testEmitsSessionReplayScreenLog() {
        let expectation = self.expectation(description: "screen log is emitted")

        self.logger.logSessionReplayScreen = expectation
        self.target.captureScreen()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.5))
    }
}
