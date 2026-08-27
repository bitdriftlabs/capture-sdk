// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import XCTest

final class NetworkAttributesTests: XCTestCase {
    func testInitialOotbFieldsContainNetworkSnapshot() {
        let attributes = NetworkAttributes()

        let fields = attributes.initialOotbFields()

        XCTAssertEqual(Set(fields.map(\.key)), ["network_type", "radio_type"])
    }

    func testStartUpdatesLoggerWithNetworkSnapshot() {
        let attributes = NetworkAttributes()
        let logger = MockCoreLogging()

        attributes.start(with: logger)

        XCTAssertEqual(logger.ootbFields["network_type"], attributes.getFields()["network_type"] as? String)
        XCTAssertEqual(logger.ootbFields["radio_type"], attributes.getFields()["radio_type"] as? String)
    }
}
