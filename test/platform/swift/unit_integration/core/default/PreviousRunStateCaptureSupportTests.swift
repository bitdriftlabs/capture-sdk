// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import CaptureLoggerBridge
import Foundation
import XCTest

final class PreviousRunStateCaptureSupportTests: XCTestCase {
    func testMainBinaryUUIDReturnsValidLowercaseUUID() throws {
        let uuid = whenReadingMainBinaryUUID()

        try thenUUIDIsValidAndLowercase(uuid)
    }

    func testSystemBootTimeReturnsPositiveValue() {
        let bootTime = whenReadingSystemBootTime()

        thenBootTimeIsPositive(bootTime)
    }

    func testSystemBootTimeIsStableAcrossCalls() {
        let first = whenReadingSystemBootTime()
        let second = whenReadingSystemBootTime()

        thenBootTimesMatch(first, second)
    }

    func testOsBuildVersionReturnsNonEmptyString() throws {
        let build = whenReadingOsBuildVersion()

        try thenBuildVersionIsNonEmpty(build)
    }

    func testOsBuildVersionIsStableAcrossCalls() throws {
        let first = whenReadingOsBuildVersion()
        let second = whenReadingOsBuildVersion()

        XCTAssertEqual(first, second)
    }
}

private extension PreviousRunStateCaptureSupportTests {
    func whenReadingMainBinaryUUID() -> String? {
        return BDPreviousRunStateCaptureSupport.mainBinaryUUID()
    }

    func whenReadingSystemBootTime() -> UInt64 {
        return BDPreviousRunStateCaptureSupport.systemBootTime()
    }

    func thenUUIDIsValidAndLowercase(_ uuid: String?) throws {
        let unwrapped = try XCTUnwrap(uuid)
        XCTAssertEqual(unwrapped, unwrapped.lowercased())
        XCTAssertNotNil(UUID(uuidString: unwrapped))
    }

    func thenBootTimeIsPositive(_ bootTime: UInt64) {
        XCTAssertGreaterThan(bootTime, 0)
    }

    func thenBootTimesMatch(_ first: UInt64, _ second: UInt64) {
        XCTAssertEqual(first, second)
    }

    func whenReadingOsBuildVersion() -> String? {
        return BDPreviousRunStateCaptureSupport.osBuildVersion()
    }

    func thenBuildVersionIsNonEmpty(_ build: String?) throws {
        let unwrapped = try XCTUnwrap(build)
        XCTAssertFalse(unwrapped.isEmpty)
    }
}
