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
