// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

@testable import Capture
import XCTest

final class DeviceAttributesTests: XCTestCase {
    func testHardwareVersionIsAvailable() {
        XCTAssertFalse(DeviceAttributes().hardwareVersion.isEmpty)
    }
}
