// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class HTTPResponseInfoTests: XCTestCase {
    func testMinimalHTTPResponse() throws {
        let response = HTTPResponse(
            result: .success,
            statusCode: nil,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: HTTPRequestInfo(method: "GET"),
            response: response,
            duration: 0.123
        )

        var fields = try XCTUnwrap(responseInfo.toFields() as? [String: String])
        XCTAssertNotNil(fields.removeValue(forKey: "_span_id"))

        XCTAssertEqual(
            [
                "_method": "GET",
                "_duration_ms": "123",
                "_result": "success",
                "_span_type": "end",
            ],
            fields
        )

        var matchingFields = try XCTUnwrap(responseInfo.toMatchingFields() as? [String: String])
        XCTAssertNotNil(matchingFields.removeValue(forKey: "_request._span_id"))

        XCTAssertEqual(["_request._method": "GET", "_request._span_type": "start"], matchingFields)
    }

    func testHTTPResponseSuccess() throws {
        let response = HTTPResponse(
            result: .success,
            headers: ["header_1": "value_1"],
            statusCode: 200,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: self.makeNewRequest(),
            response: response,
            duration: 0.123,
            extraFields: ["key2": "value2"]
        )

        XCTAssertEqual(
            [
                "_method": "method",
                "_host": "host",
                "_path": "/path/12345",
                "_query": "query",
                "_span_id": "span_id",
                "_span_type": "end",
                "key2": "value2",
                "_status_code": "200",
                "_duration_ms": "123",
                "_result": "success",
                "key": "value",
            ],
            try XCTUnwrap(responseInfo.toFields() as? [String: String])
        )

        XCTAssertEqual(
            [
                "_request._query": "query",
                "_request._host": "host",
                "_request._method": "method",
                "_request._path": "/path/12345",
                "_request._span_id": "span_id",
                "_request._span_type": "start",
                "_request.key": "value",
                "_headers.header_1": "value_1",
                "_request._headers.request_header": "request_value",
            ],
            try XCTUnwrap(responseInfo.toMatchingFields() as? [String: String])
        )
    }

    func testHTTPRequestExplicitPathTemplate() throws {
        let response = HTTPResponse(
            result: .success,
            path: .init(
                value: try XCTUnwrap(self.makeURL().normalizedPath())
            ),
            statusCode: nil,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: HTTPRequestInfo(
                method: "GET",
                host: "host",
                path: .init(
                    value: try XCTUnwrap(self.makeURL().normalizedPath()),
                    template: "/testing/<id>"
                ),
                query: "query",
                spanID: "foo"
            ),
            response: response,
            duration: 0.135
        )

        XCTAssertEqual(
            [
                "_method": "GET",
                "_host": "host",
                "_path": "/path/12345",
                "_path_template": "/testing/<id>",
                "_query": "query",
                "_span_id": "foo",
                "_span_type": "end",
                "_duration_ms": "135",
                "_result": "success",
            ],
            try XCTUnwrap(responseInfo.toFields() as? [String: String])
        )
    }

    func testHTTPResponseRequestAttributesOverride() {
        let response = HTTPResponse(
            result: .success,
            statusCode: nil,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: self.makeNewRequest(),
            response: response,
            duration: 0.135
        )

        XCTAssertEqual(
            [
                "_method": "method",
                "_host": "host",
                "_path": "/path/12345",
                "_query": "query",
                "_span_id": "span_id",
                "_span_type": "end",
                "_duration_ms": "135",
                "_result": "success",
                "key": "value",
            ],
            try XCTUnwrap(responseInfo.toFields() as? [String: String])
        )

        let responseWithOverriddenAttributes = HTTPResponse(
            result: .success,
            host: "foo.com",
            path: .init(value: "/foo_path/12345", template: "/foo_path/{explicit_id}"),
            query: "foo_query",
            statusCode: nil,
            error: nil
        )

        let responseInfoWithOverriddenAttributes = HTTPResponseInfo(
            requestInfo: self.makeNewRequest(),
            response: responseWithOverriddenAttributes,
            duration: 0.135
        )

        XCTAssertEqual(
            [
                "_method": "method",
                "_host": "foo.com",
                "_path": "/foo_path/12345",
                "_path_template": "/foo_path/{explicit_id}",
                "_query": "foo_query",
                "_span_id": "span_id",
                "_span_type": "end",
                "_duration_ms": "135",
                "_result": "success",
                "key": "value",
            ],
            try XCTUnwrap(responseInfoWithOverriddenAttributes.toFields() as? [String: String])
        )
    }

    func testHTTPResponseCancellation() {
        // There is no URL for canceled case since for such case `URLResponse`, that we usually use to
        // retrieve response URL, doesn't exist.
        let url = self.makeURL()
        let response = HTTPResponse(
            result: .canceled,
            host: url.host,
            path: .init(value: url.path),
            query: url.query,
            statusCode: nil,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: self.makeNewRequest(),
            response: response,
            duration: 0.456
        )

        XCTAssertEqual(
            [
                "_method": "method",
                "_host": "host",
                "_path": "/path/12345",
                "_query": "query",
                "_span_id": "span_id",
                "_span_type": "end",
                "_duration_ms": "456",
                "_result": "canceled",
                "key": "value",
            ],
            try XCTUnwrap(responseInfo.toFields() as? [String: String])
        )

        XCTAssertEqual(
            [
                "_request._query": "query",
                "_request._host": "host",
                "_request._method": "method",
                "_request._path": "/path/12345",
                "_request._span_id": "span_id",
                "_request._span_type": "start",
                "_request.key": "value",
                "_request._headers.request_header": "request_value",
            ],
            try XCTUnwrap(responseInfo.toMatchingFields() as? [String: String])
        )
    }

    func testHTTPResponseFailureWithCodeWithoutMessageWithoutErrorType() throws {
        let url = self.makeURL()
        let response = HTTPResponse(
            result: .failure,
            host: url.host,
            path: .init(value: url.path),
            query: url.query,
            statusCode: 400,
            error: nil
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: self.makeNewRequest(),
            response: response,
            duration: 0.789
        )

        XCTAssertEqual(
            [
                "_method": "method",
                "_host": "host",
                "_path": "/path/12345",
                "_query": "query",
                "_span_id": "span_id",
                "_span_type": "end",
                "_status_code": "400",
                "_duration_ms": "789",
                "_result": "failure",
                "key": "value",
            ],
            try XCTUnwrap(responseInfo.toFields() as? [String: String])
        )

        XCTAssertEqual(
            [
                "_request._query": "query",
                "_request._host": "host",
                "_request._method": "method",
                "_request._path": "/path/12345",
                "_request._span_id": "span_id",
                "_request._span_type": "start",
                "_request.key": "value",
                "_request._headers.request_header": "request_value",
            ],
            try XCTUnwrap(responseInfo.toMatchingFields() as? [String: String])
        )
    }

    func testHTTPResponseFailureWithoutCodeWithMessageWithErrorType() {
        let error = NSError(
            domain: "io.bitdrift.capture",
            code: 100,
            userInfo: [
                String(kCFErrorLocalizedDescriptionKey): "test_description",
            ]
        )
        let url = self.makeURL()
        let response = HTTPResponse(
            result: .failure,
            host: url.host,
            path: .init(value: url.path),
            query: url.query,
            statusCode: nil,
            error: error
        )
        let responseInfo = HTTPResponseInfo(
            requestInfo: self.makeNewRequest(),
            response: response,
            duration: 0.135
        )

        XCTAssertEqual(
            [
                "_method": "method",
                "_host": "host",
                "_path": "/path/12345",
                "_query": "query",
                "_span_id": "span_id",
                "_span_type": "end",
                "_duration_ms": "135",
                "_result": "failure",
                "_error_code": "100",
                "_error_message": "test_description",
                "key": "value",
            ],
            try XCTUnwrap(responseInfo.toFields() as? [String: String])
        )

        XCTAssertEqual(
            [
                "_request._query": "query",
                "_request._host": "host",
                "_request._method": "method",
                "_request._path": "/path/12345",
                "_request._span_id": "span_id",
                "_request._span_type": "start",
                "_request.key": "value",
                "_request._headers.request_header": "request_value",
            ],
            try XCTUnwrap(responseInfo.toMatchingFields() as? [String: String])
        )
    }

    // MARK: - Helper

    private func makeNewRequest() -> HTTPRequestInfo {
        let url = self.makeURL()

        return HTTPRequestInfo(
            method: "method",
            host: url.host ?? "",
            path: url.normalizedPath().flatMap { .init(value: $0) },
            query: url.query,
            headers: ["request_header": "request_value"],
            spanID: "span_id",
            extraFields: ["key": "value"]
        )
    }

    private func makeURL() -> URL {
        return URL(staticString: "https://host/path/12345?query")
    }
}
