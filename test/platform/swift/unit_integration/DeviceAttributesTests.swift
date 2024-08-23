// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class DeviceAttributesTests: XCTestCase {
    func testLocale() {
        let deviceAttributes = DeviceAttributes()
        // Confirm that attributes are initialized with the `locale` field value.
        XCTAssertEqual("en_US", deviceAttributes.getFields()["_locale"] as? String)

        deviceAttributes.start()
        // Confirm that the `locale` field looks OK after starting device attributes.
        XCTAssertEqual("en_US", deviceAttributes.getFields()["_locale"] as? String)
    }
}
