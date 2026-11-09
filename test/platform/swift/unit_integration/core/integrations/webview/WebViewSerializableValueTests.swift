// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class WebViewSerializableValueTests: XCTestCase {
    // MARK: - Decoding

    func testDecodeString() throws {
        let sut = try givenDecodedValue(from: "\"hello\"")
        XCTAssertEqual(sut, .string("hello"))
    }

    func testDecodeIntegerAsNumber() throws {
        let sut = try givenDecodedValue(from: "42")
        XCTAssertEqual(sut, .number(42))
    }

    func testDecodeDoubleAsNumber() throws {
        let sut = try givenDecodedValue(from: "3.5")
        XCTAssertEqual(sut, .number(3.5))
    }

    func testDecodeTrue() throws {
        let sut = try givenDecodedValue(from: "true")
        XCTAssertEqual(sut, .bool(true))
    }

    func testDecodeFalse() throws {
        let sut = try givenDecodedValue(from: "false")
        XCTAssertEqual(sut, .bool(false))
    }

    func testDecodeNull() throws {
        let sut = try givenDecodedValue(from: "null")
        XCTAssertEqual(sut, .null)
    }

    func testDecodeArray() throws {
        let sut = try givenDecodedValue(from: "[1, \"two\", true, null]")
        XCTAssertEqual(sut, .array([.number(1), .string("two"), .bool(true), .null]))
    }

    func testDecodeObject() throws {
        let sut = try givenDecodedValue(from: "{\"a\": 1, \"b\": \"two\"}")
        XCTAssertEqual(sut, .object(["a": .number(1), "b": .string("two")]))
    }

    func testDecodeNestedObjectInsideArray() throws {
        let sut = try givenDecodedValue(from: "[{\"a\": 1}]")
        XCTAssertEqual(sut, .array([.object(["a": .number(1)])]))
    }

    // MARK: - Encoding round-trip

    func testEncodeThenDecodeProducesEqualValue() throws {
        let original = WebViewSerializableValue.object([
            "name": .string("bitdrift"),
            "count": .number(3),
            "active": .bool(true),
            "missing": .null,
            "tags": .array([.string("a"), .string("b")]),
        ])

        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(WebViewSerializableValue.self, from: data)

        XCTAssertEqual(original, decoded)
    }

    // MARK: - fieldStringValue

    func testFieldStringValueForString() {
        XCTAssertEqual(WebViewSerializableValue.string("hello").fieldStringValue, "hello")
    }

    func testFieldStringValueForNumber() {
        XCTAssertEqual(WebViewSerializableValue.number(3).fieldStringValue, "3.0")
    }

    func testFieldStringValueForBool() {
        XCTAssertEqual(WebViewSerializableValue.bool(true).fieldStringValue, "true")
        XCTAssertEqual(WebViewSerializableValue.bool(false).fieldStringValue, "false")
    }

    func testFieldStringValueForNull() {
        XCTAssertEqual(WebViewSerializableValue.null.fieldStringValue, "null")
    }

    func testFieldStringValueForArrayEncodesAsJSON() {
        let value = WebViewSerializableValue.array([.number(1), .number(2)])
        XCTAssertEqual(value.fieldStringValue, "[1,2]")
    }

    func testFieldStringValueForObjectEncodesAsSortedJSON() {
        let value = WebViewSerializableValue.object(["b": .number(2), "a": .string("x")])
        XCTAssertEqual(value.fieldStringValue, "{\"a\":\"x\",\"b\":2}")
    }
}

private extension WebViewSerializableValueTests {
    func givenDecodedValue(from json: String) throws -> WebViewSerializableValue {
        try JSONDecoder().decode(WebViewSerializableValue.self, from: Data(json.utf8))
    }
}
