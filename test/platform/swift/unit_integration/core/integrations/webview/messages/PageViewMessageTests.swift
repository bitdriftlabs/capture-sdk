// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class PageViewMessageTests: XCTestCase {
    private var sut: PageViewMessage!

    func testMakeLoggingActionWithStartActionReturnsStartSpan() throws {
        try givenPageViewMessage(
            action: "start",
            spanId: "span-1",
            url: "https://example.com/page?source=test",
            reason: "initial",
            timestamp: 1_700_000_000_000
        )
        let action = whenMakingLoggingAction()

        guard case let .startSpan(id, name, level, fields, startTimeInterval, parentSpanID)? = action else {
            XCTFail("expected .startSpan action, got \(String(describing: action))")
            return
        }

        XCTAssertEqual(id, "span-1")
        XCTAssertEqual(name, "webview.pageView")
        XCTAssertEqual(level, .info)
        XCTAssertEqual((fields as? [String: String])?["_url"], "https://example.com/page?source=test")
        XCTAssertEqual((fields as? [String: String])?["_host"], "example.com")
        XCTAssertEqual((fields as? [String: String])?["_path"], "/page")
        XCTAssertEqual((fields as? [String: String])?["_query"], "source=test")
        XCTAssertEqual((fields as? [String: String])?["_reason"], "initial")
        XCTAssertEqual(startTimeInterval, 1_700_000_000)
        XCTAssertNil(parentSpanID)
    }

    func testMakeLoggingActionWithEndActionReturnsEndSpan() throws {
        try givenPageViewMessage(action: "end", spanId: "span-1", timestamp: 1_700_000_000_000)
        let action = whenMakingLoggingAction()

        guard case let .endSpan(id, result, _, endTimeInterval)? = action else {
            XCTFail("expected .endSpan action, got \(String(describing: action))")
            return
        }

        XCTAssertEqual(id, "span-1")
        XCTAssertEqual(result, .success)
        XCTAssertEqual(endTimeInterval, 1_700_000_000)
    }

    func testMakeLoggingActionOmitsEmptyQuery() throws {
        try givenPageViewMessage(action: "start", url: "https://example.com/page?")
        let action = whenMakingLoggingAction()

        guard case let .startSpan(_, _, _, fields, _, _)? = action else {
            XCTFail("expected .startSpan action, got \(String(describing: action))")
            return
        }

        XCTAssertNil((fields as? [String: String])?["_query"])
    }

    func testMakeLoggingActionWithUnknownActionReturnsNil() throws {
        try givenPageViewMessage(action: "resume")
        let action = whenMakingLoggingAction()
        XCTAssertNil(action)
    }
}

private extension PageViewMessageTests {
    func givenPageViewMessage(
        action: String,
        spanId: String = "span-1",
        url: String = "https://example.com",
        reason: String = "navigation",
        durationMs: Double? = nil,
        timestamp: Int64 = 1_700_000_000_000
    ) throws {
        let durationMsJSON: String = durationMs.map { String($0) } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "pageView",
            "timestamp": \(timestamp),
            "action": "\(action)",
            "spanId": "\(spanId)",
            "url": "\(url)",
            "reason": "\(reason)",
            "durationMs": \(durationMsJSON)
        }
        """
        sut = try decodeWebViewMessage(PageViewMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
