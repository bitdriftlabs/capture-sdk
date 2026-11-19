// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import Foundation
import XCTest

final class FieldExtensionTests: XCTestCase {
    func testSessionReplayCaptureIsEncodedAsData() throws {
        let field = try Field.make(
            key: "foo",
            value: SessionReplayCapture(data: try XCTUnwrap("test".data(using: .utf8)))
        )

        XCTAssert(field.data is Data)
    }

    func testEncodableEncodedAsString() throws {
        let field = try Field.make(key: "foo", value: "bar")

        let stringValue = try XCTUnwrap(field.data as? String)
        XCTAssertEqual(stringValue, "bar")
    }

    func testThrowsOnEncodingFailure() {
        struct FailingEncodable: Encodable {
            struct Error: Swift.Error {}

            func encode(to _: Encoder) throws {
                throw Error()
            }
        }

        XCTAssertThrowsError(try Field.make(key: "foo", value: FailingEncodable()))
    }
}
