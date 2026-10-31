// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

@testable import Capture
import CaptureLoggerBridge
import XCTest

final class PreviousRunCurrentStateTests: XCTestCase {
    func test_create_embedsBuildVersionInOsVersion() throws {
        let state = PreviousRunCurrentState.create(osVersion: "18.0")

        let build = try XCTUnwrap(BDPreviousRunStateCaptureSupport.osBuildVersion())
        XCTAssertEqual(state.osVersion, "18.0 (\(build))")
    }
}
