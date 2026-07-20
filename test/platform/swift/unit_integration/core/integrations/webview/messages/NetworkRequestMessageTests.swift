// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class NetworkRequestMessageTests: XCTestCase {
    private var sut: NetworkRequestMessage!

    func testMakeLoggingActionWithSuccessfulRequestReturnsNetworkAction() throws {
        try givenNetworkRequestMessage(
            requestId: "req_1",
            method: "GET",
            url: "https://example.com/fe/ping?q=test",
            statusCode: 200,
            durationMs: 250,
            success: true,
            requestType: "fetch"
        )
        let action = whenMakingLoggingAction()

        try thenActionIsNetwork(action) { requestFields, responseFields in
            XCTAssertEqual(requestFields["_method"], "GET")
            XCTAssertEqual(requestFields["_host"], "example.com")
            XCTAssertEqual(requestFields["_path"], "/fe/ping")
            XCTAssertEqual(requestFields["_query"], "q=test")
            XCTAssertEqual(requestFields["_span_id"], "req_1")
            XCTAssertEqual(requestFields["_request_type"], "fetch")

            XCTAssertEqual(responseFields["_status_code"], "200")
            XCTAssertEqual(responseFields["_result"], "success")
            XCTAssertEqual(responseFields["_duration_ms"], "250")
        }
    }

    func testMakeLoggingActionWithFailedRequestIncludesErrorMessage() throws {
        try givenNetworkRequestMessage(success: false, error: "network unreachable")
        let action = whenMakingLoggingAction()

        try thenActionIsNetwork(action) { _, responseFields in
            XCTAssertEqual(responseFields["_result"], "failure")
            XCTAssertEqual(responseFields["_error_message"], "network unreachable")
        }
    }

    func testMakeLoggingActionWithoutTimingOmitsMetricsFields() throws {
        try givenNetworkRequestMessage(timing: nil)
        let action = whenMakingLoggingAction()

        try thenActionIsNetwork(action) { _, responseFields in
            XCTAssertNil(responseFields["_response_body_bytes_received_count"])
            XCTAssertNil(responseFields["_protocol"])
        }
    }

    func testMakeLoggingActionWithTimingIncludesProtocolMetric() throws {
        try givenNetworkRequestMessage(timing: WebViewResourceTiming(
            name: nil, entryType: nil, startTime: nil, duration: nil, initiatorType: nil,
            nextHopProtocol: "h2", workerStart: nil, redirectStart: nil, redirectEnd: nil,
            fetchStart: nil, domainLookupStart: nil, domainLookupEnd: nil, connectStart: nil,
            connectEnd: nil, secureConnectionStart: nil, requestStart: nil, responseStart: nil,
            responseEnd: nil, transferSize: nil, encodedBodySize: nil, decodedBodySize: nil,
            responseStatus: nil, serverTiming: nil
        ))
        let action = whenMakingLoggingAction()

        try thenActionIsNetwork(action) { _, responseFields in
            XCTAssertEqual(responseFields["_protocol"], "h2")
        }
    }

    func testMakeLoggingActionWithInvalidURLReturnsNilAction() throws {
        try givenNetworkRequestMessage(url: "https://example.com:notanumber/ping")
        let action = whenMakingLoggingAction()
        XCTAssertNil(action)
    }
}

private extension NetworkRequestMessageTests {
    func givenNetworkRequestMessage(
        requestId: String = "req_1",
        method: String = "GET",
        url: String = "https://example.com/fe/ping?q=test",
        statusCode: Int = 200,
        durationMs: Double = 100,
        success: Bool = true,
        error: String? = nil,
        requestType: String = "fetch",
        timing: WebViewResourceTiming? = nil
    ) throws {
        let timingJSON = try timing.map { try encodeToJSON($0) } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "networkRequest",
            "timestamp": 1700000000000,
            "requestId": "\(requestId)",
            "method": "\(method)",
            "url": "\(url)",
            "statusCode": \(statusCode),
            "durationMs": \(durationMs),
            "success": \(success),
            "error": \(error.map { "\"\($0)\"" } ?? "null"),
            "requestType": "\(requestType)",
            "timing": \(timingJSON)
        }
        """
        sut = try decodeWebViewMessage(NetworkRequestMessage.self, from: json)
    }

    func encodeToJSON(_ timing: WebViewResourceTiming) throws -> String {
        let data = try JSONEncoder().encode(timing)
        return try XCTUnwrap(String(data: data, encoding: .utf8))
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }

    func thenActionIsNetwork(
        _ action: WebViewLoggingAction?,
        assertFields: ([String: String], [String: String]) -> Void
    ) throws {
        guard case let .network(request, response)? = action else {
            XCTFail("expected .network action, got \(String(describing: action))")
            return
        }

        let requestFields = try XCTUnwrap(request.toFields() as? [String: String])
        let responseFields = try XCTUnwrap(response.toFields() as? [String: String])
        assertFields(requestFields, responseFields)
    }
}
