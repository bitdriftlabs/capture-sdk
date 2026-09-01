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

        let initialFields = Dictionary(
            uniqueKeysWithValues: attributes.initialOotbFields().compactMap { field in
                (field.data as? String).map { (field.key, $0) }
            }
        )
        XCTAssertEqual(logger.ootbFields["network_type"], initialFields["network_type"])
        XCTAssertEqual(logger.ootbFields["radio_type"], initialFields["radio_type"])
        XCTAssertEqual(logger.ootbFieldUpdates.prefix(2).map(\.key), ["radio_type", "network_type"])
    }

    func testTelephonyDoesNotRepublishAnUnchangedRadioField() {
        let attributes = TelephonyNetworkInfo()
        let logger = MockCoreLogging()

        attributes.start(with: logger)
        let updateCount = logger.ootbFieldUpdateCount

        attributes.dataServiceIdentifierDidChange("test-service")
        _ = attributes.initialOotbFields()

        XCTAssertEqual(logger.ootbFieldUpdateCount, updateCount)
        XCTAssertNotNil(logger.ootbFields["radio_type"])
    }
}
