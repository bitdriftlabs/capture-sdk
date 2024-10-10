// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import XCTest

final class ResourceUtilizationTargetTest: XCTestCase {
    func testTargetDoesNotCrash() {
        let target = ResourceUtilizationTarget(
            storageProvider: MockStorageProvider(),
            timeProvider: MockTimeProvider()
        )

        let logger = MockCoreLogging()
        target.logger = logger

        let expectation = self.expectation(description: "resource utilization log is emitted")
        logger.logResourceUtilizationExpectation = expectation

        target.tick()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 0.5))
    }
}
