// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class WebVitalMessageTests: XCTestCase {
    private var sut: WebVitalMessage!

    func testMakeLoggingActionForLCPReturnsCompleteSpan() throws {
        try givenWebVitalMessage(
            metricName: "LCP",
            value: 2_500,
            rating: "good",
            timestamp: 1_700_000_000_000
        )
        let action = whenMakingLoggingAction()

        guard case let .completeSpan(name, level, _, startTimeInterval, endTimeInterval, _, result)? = action else {
            XCTFail("expected .completeSpan action, got \(String(describing: action))")
            return
        }

        XCTAssertEqual(name, "webview.webVital")
        XCTAssertEqual(level, .debug)
        XCTAssertEqual(result, .success)
        XCTAssertEqual(endTimeInterval, 1_700_000_000)
        XCTAssertEqual(startTimeInterval, 1_700_000_000 - 2.5)
    }

    func testMakeLoggingActionForNonSpanMetricReturnsLogAction() throws {
        try givenWebVitalMessage(metricName: "CLS", rating: "good")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.webVital", level: .debug)
    }

    func testMakeLoggingActionWithPoorRatingLogsAtWarningWithFailureResult() throws {
        try givenWebVitalMessage(metricName: "LCP", rating: "poor")
        let action = whenMakingLoggingAction()

        guard case let .completeSpan(_, level, _, _, _, _, result)? = action else {
            XCTFail("expected .completeSpan action, got \(String(describing: action))")
            return
        }

        XCTAssertEqual(level, .warning)
        XCTAssertEqual(result, .failure)
    }

    func testMakeLoggingActionWithNeedsImprovementRatingLogsAtInfoWithFailureResult() throws {
        try givenWebVitalMessage(metricName: "INP", rating: "needs-improvement")
        let action = whenMakingLoggingAction()

        guard case let .completeSpan(_, level, _, _, _, _, result)? = action else {
            XCTFail("expected .completeSpan action, got \(String(describing: action))")
            return
        }

        XCTAssertEqual(level, .info)
        XCTAssertEqual(result, .failure)
    }

    func testMakeLoggingActionIncludesMetricFields() throws {
        try givenWebVitalMessage(
            metricName: "FCP",
            value: 1_200,
            rating: "good",
            delta: 100,
            metricId: "metric-1",
            navigationType: "navigate",
            url: "https://example.com/page"
        )
        let action = whenMakingLoggingAction()

        guard case let .completeSpan(_, _, fields, _, _, _, _)? = action else {
            XCTFail("expected .completeSpan action, got \(String(describing: action))")
            return
        }

        let stringFields = (fields as? [String: String]) ?? [:]
        XCTAssertEqual(stringFields["_metric"], "FCP")
        XCTAssertEqual(stringFields["_value"], "1200.0")
        XCTAssertEqual(stringFields["_rating"], "good")
        XCTAssertEqual(stringFields["_delta"], "100.0")
        XCTAssertEqual(stringFields["_metric_id"], "metric-1")
        XCTAssertEqual(stringFields["_navigation_type"], "navigate")
        XCTAssertEqual(stringFields["_page_url"], "https://example.com/page")
        XCTAssertNil(stringFields["_timestamp"])
    }

    func testMakeLoggingActionWithEmptyEntriesOmitsEntriesField() throws {
        try givenWebVitalMessage(metricName: "CLS", rating: "good")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.webVital", level: .debug) { fields in
            XCTAssertNil(fields["_entries"])
        }
    }

    func testMakeLoggingActionResolvesParentSpanIdFromValidUUIDString() throws {
        try givenWebVitalMessage(
            metricName: "CLS",
            rating: "good",
            parentSpanId: "11111111-1111-1111-1111-111111111111"
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.webVital", level: .debug) { fields in
            XCTAssertEqual(fields["_span_parent_id"], "11111111-1111-1111-1111-111111111111")
        }
    }
}

private extension WebVitalMessageTests {
    func givenWebVitalMessage(
        metricName: String,
        value: Double = 1_000,
        rating: String,
        delta: Double = 0,
        metricId: String = "metric-id",
        navigationType: String = "navigate",
        parentSpanId: String? = nil,
        url: String? = nil,
        timestamp: Int64 = 1_700_000_000_000
    ) throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "webVital",
            "timestamp": \(timestamp),
            "metric": {
                "name": "\(metricName)",
                "value": \(value),
                "rating": "\(rating)",
                "delta": \(delta),
                "id": "\(metricId)",
                "navigationType": "\(navigationType)",
                "entries": []
            },
            "parentSpanId": \(parentSpanId.map { "\"\($0)\"" } ?? "null"),
            "url": \(url.map { "\"\($0)\"" } ?? "null")
        }
        """
        sut = try decodeWebViewMessage(WebVitalMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
