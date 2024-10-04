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

final class ResourceUtilizationTargetTests: XCTestCase {
    private var storageProvider: MockStorageProvider!
    private var timeProvider: MockTimeProvider!
    private var logger: MockCoreLogging!
    private var target: Capture.ResourceUtilizationTarget!

    override func setUp() {
        super.setUp()

        self.storageProvider = MockStorageProvider()
        self.timeProvider = MockTimeProvider()
        self.logger = MockCoreLogging()

        self.target = ResourceUtilizationTarget(
            storageProvider: self.storageProvider,
            timeProvider: self.timeProvider
        )

        self.target.logger = self.logger
    }

    func testResourceUtilizationTargetDoesNotCrash() {
        run_resource_utilization_target_test(self.target)
    }

    func testEmitsDiskUsageFieldsOnceEvery24H() {
        let firstLogExpectation = self.expectation(description: "resource utilization log is emitted")
        self.logger.logResourceUtilizationExpectation = firstLogExpectation

        self.target.tick()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [firstLogExpectation], timeout: 0.5))
        XCTAssertEqual(1, self.logger.resourceUtilizationLogs.count)
        XCTAssertNotNil(self.logger.resourceUtilizationLogs[0]
                            .fields[DiskUsageSnapshot.FieldKey.documentsDirectory.rawValue])

        let secondLogExpectation = self.expectation(description: "resource utilization log is not emitted")
        self.logger.logResourceUtilizationExpectation = secondLogExpectation

        self.target.tick()

        // App Disk Usage fields are not emitted within 24 hours of the previous emission of these logs.
        XCTAssertEqual(.completed, XCTWaiter().wait(for: [secondLogExpectation], timeout: 0.5))
        XCTAssertEqual(2, self.logger.resourceUtilizationLogs.count)
        XCTAssertNil(self.logger.resourceUtilizationLogs[1]
                        .fields[DiskUsageSnapshot.FieldKey.documentsDirectory.rawValue])

        // Advance by more than 24 hours
        self.timeProvider.advanceBy(timeInterval: 25 * 60 * 60)

        let thirdLogExpectation = self.expectation(
            description: "another resource utilization log is emitted"
        )
        self.logger.logResourceUtilizationExpectation = thirdLogExpectation

        self.target.tick()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [thirdLogExpectation], timeout: 0.5))
        XCTAssertEqual(3, self.logger.resourceUtilizationLogs.count)
        XCTAssertNotNil(self.logger.resourceUtilizationLogs[2]
                            .fields[DiskUsageSnapshot.FieldKey.documentsDirectory.rawValue])
    }
}
