// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class HTTPRequestInfoTests: XCTestCase {
    func testMinimalHTTPRequest() throws {
        let requestInfo = HTTPRequestInfo(
            method: "method",
            extraFields: ["key": "value"]
        )

        var fields = try XCTUnwrap(requestInfo.toFields() as? [String: String])
        XCTAssertNotNil(fields.removeValue(forKey: "_span_id"))

        XCTAssertEqual(
            [
                "_method": "method",
                "_span_type": "start",
                "key": "value",
            ],
            fields
        )

        XCTAssertTrue(requestInfo.toMatchingFields().isEmpty)
    }

    func testHTTPRequest() throws {
        let requestInfo = HTTPRequestInfo(
            method: "method",
            host: "host",
            path: .init(value: "/path/12345"),
            query: "query",
            headers: ["header_1": "value_1"],
            bytesExpectedToSendCount: 1,
            spanID: "span_id",
            extraFields: ["key": "value"]
        )

        XCTAssertEqual(
            [
                "_request_body_bytes_expected_to_send_count": "1",
                "_method": "method",
                "_host": "host",
                "_path": "/path/12345",
                "_query": "query",
                "_span_id": "span_id",
                "_span_type": "start",
                "key": "value",
            ],
            try XCTUnwrap(requestInfo.toFields() as? [String: String])
        )

        XCTAssertEqual(
            ["_headers.header_1": "value_1"],
            requestInfo.toMatchingFields() as? [String: String]
        )
    }

    func testHTTPRequestInitializationWithURLRequest() throws {
        var request = URLRequest(url: URL(staticString: "https://www.bitdrft.io/test/12345?q=foo"))
        request.addValue("value", forHTTPHeaderField: "key")
        request.addValue("/test/{explicit_id}", forHTTPHeaderField: kCapturePathTemplateHeaderKey)

        let info = HTTPRequestInfo(urlRequest: request, extraFields: nil)
        var fields = try XCTUnwrap(info.toFields() as? [String: String])

        XCTAssertNotNil(fields.removeValue(forKey: "_span_id"))
        XCTAssertEqual([
            "_host": "www.bitdrft.io",
            "_method": "GET",
            "_path": "/test/12345",
            "_path_template": "/test/{explicit_id}",
            "_query": "q=foo",
            "_span_type": "start",
        ], fields)
    }
}
