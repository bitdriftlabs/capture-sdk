import Capture
import XCTest

final class IntegrationTests: XCTestCase {
    func testCapture() {
        Capture.Logger.start(withAPIKey: "foo", sessionStrategy: .fixed())
        XCTAssertNotNil(Capture.Logger.sessionID)
    }
}
