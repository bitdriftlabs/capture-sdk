import Capture
import CaptureCocoaLumberjack
import CaptureMocks
import CocoaLumberjackSwift
import XCTest

final class CaptureCocoaLumberjackIntegrationTests: XCTestCase {
    func testAddingCaptureDDLogger() {
        let expectation = self.expectation(description: "all logs are forwarded to Capture logger")
        expectation.expectedFulfillmentCount = 5

        let logger = MockLogging()
        logger.logExpectation = expectation

        Integration.cocoaLumberjack().start(with: logger)

        DDLogVerbose("Verbose")
        DDLogDebug("Debug")
        DDLogInfo("Info")
        DDLogWarn("Warn")
        DDLogError("Error")

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 5))
        XCTAssertEqual(logger.logs.map(\.message), [
            "Verbose",
            "Debug",
            "Info",
            "Warn",
            "Error",
        ])
    }
}
