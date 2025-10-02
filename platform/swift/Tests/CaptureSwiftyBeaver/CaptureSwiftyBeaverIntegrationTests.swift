import Capture
import CaptureMocks
import CaptureSwiftyBeaver
import SwiftyBeaver
import XCTest

final class CaptureSwiftyBeaverIntegrationTests: XCTestCase {
    func testAddingCaptureSwiftyBeaverLogger() {
        let expectation = self.expectation(description: "all logs are forwarded to Capture logger")
        expectation.expectedFulfillmentCount = 5

        let logger = MockLogging()
        logger.logExpectation = expectation

        Integration.swiftyBeaver().start(with: logger)

        SwiftyBeaver.verbose("Verbose")
        SwiftyBeaver.debug("Debug")
        SwiftyBeaver.info("Info")
        SwiftyBeaver.warning("Warning")
        SwiftyBeaver.error("Error")

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 5))
        XCTAssertEqual(logger.logs.map(\.message), [
            "Verbose",
            "Debug",
            "Info",
            "Warning",
            "Error",
        ])
    }
}
