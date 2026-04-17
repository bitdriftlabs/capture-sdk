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

final class ResourceUtilizationControllerTests: XCTestCase {
    private var storageProvider: MockStorageProvider!
    private var timeProvider: MockTimeProvider!
    private var logger: MockCoreLogging!
    private var target: Capture.ResourceUtilizationController!

    override func setUp() {
        super.setUp()

        self.storageProvider = MockStorageProvider()
        self.timeProvider = MockTimeProvider()
        self.logger = MockCoreLogging()

        self.target = ResourceUtilizationController(
            storageProvider: self.storageProvider,
            timeProvider: self.timeProvider,
            queue: .main
        )

        self.target.logger = self.logger
    }

    func testResourceUtilizationControllerDoesNotCrash() {
        run_resource_utilization_target_test(self.target)
    }

    func testEmitsDiskUsageFieldsOnceEvery24H() throws {
        let firstLogExpectation = self.expectation(description: "resource utilization log is emitted")
        self.logger.logResourceUtilizationExpectation = firstLogExpectation

        self.target.tick()

        wait(for: [firstLogExpectation], timeout: 1.0)
        XCTAssertEqual(1, self.logger.resourceUtilizationLogs.count)
        let firstLog = try XCTUnwrap(self.logger.resourceUtilizationLogs.first)
        XCTAssertNotNil(firstLog.fields[DiskUsageSnapshot.FieldKey.documentsDirectory.rawValue])

        let secondLogExpectation = self.expectation(description: "resource utilization log is not emitted")
        self.logger.logResourceUtilizationExpectation = secondLogExpectation

        self.target.tick()

        // App Disk Usage fields are not emitted within 24 hours of the previous emission of these logs.
        wait(for: [secondLogExpectation], timeout: 1.0)
        XCTAssertEqual(2, self.logger.resourceUtilizationLogs.count)
        let secondLog = try XCTUnwrap(self.logger.resourceUtilizationLogs.last)
        XCTAssertNil(secondLog.fields[DiskUsageSnapshot.FieldKey.documentsDirectory.rawValue])

        // Advance by more than 24 hours
        self.timeProvider.advanceBy(timeInterval: 25 * 60 * 60)

        let thirdLogExpectation = self.expectation(
            description: "another resource utilization log is emitted"
        )
        self.logger.logResourceUtilizationExpectation = thirdLogExpectation

        self.target.tick()

        wait(for: [thirdLogExpectation], timeout: 1.0)
        XCTAssertEqual(3, self.logger.resourceUtilizationLogs.count)
        let thirdLog = try XCTUnwrap(self.logger.resourceUtilizationLogs.last)
        XCTAssertNotNil(thirdLog.fields[DiskUsageSnapshot.FieldKey.documentsDirectory.rawValue])
    }

    func testMemorySnapshotIncludesIsMemoryLowFieldWhenThresholdProvided() {
        let snapshot = MemorySnapshot(
            appTotalMemoryLimitKB: 100,
            appTotalMemoryUsedKB: 100,
            deviceTotalMemoryKB: 200,
            lowMemoryConfigThresholdPercent: 90,
            relativeTimestamp: nil,
            timeSinceDeviceBoot: 1,
            sequenceNumber: 1,
            timeToCaptureMicroseconds: 0
        )

        let fields = snapshot.toDictionary()
        XCTAssertNotNil(fields["_is_memory_low"])
    }
}
