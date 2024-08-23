// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

private struct MockEncodable: Encodable {
    let fooBar: String = "zoo"
}

final class EncodableExtensionsTests: XCTestCase {
    func testEncodingToString() throws {
        let testCases: [(Encodable, String)] = [
            ("foo", "foo"),
            (Date(timeIntervalSince1970: 0), "1970-01-01T00:00:00.000Z"),
            ("foo".data(using: .utf8), "Zm9v"),
            (123, "123"),
            (Optional(true), "true"),
            (Bool?.none, "null"),
            (["foo": "bar"], "{\"foo\":\"bar\"}"),
            (MockEncodable(), "{\"fooBar\":\"zoo\"}"),
        ]

        for testCase in testCases {
            let (input, expectedResult) = testCase
            XCTAssertEqual(try input.encodeToString(), expectedResult)
        }
    }
}
