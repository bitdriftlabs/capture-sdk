// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class LifecycleMessageTests: XCTestCase {
    private var sut: LifecycleMessage!

    func testMakeLoggingActionLogsAtDebugLevel() throws {
        try givenLifecycleMessage()
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.lifecycle", level: .info, type: .ux)
    }

    func testMakeLoggingActionIncludesEventAndPerformanceTime() throws {
        try givenLifecycleMessage(event: "DOMContentLoaded", performanceTime: 123.456)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.lifecycle", level: .info) { fields in
            XCTAssertEqual(fields["_event"], "DOMContentLoaded")
            XCTAssertEqual(fields["_performance_time"], "123.456")
        }
    }

    func testMakeLoggingActionIncludesVisibilityStateWhenPresent() throws {
        try givenLifecycleMessage(visibilityState: "hidden")
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.lifecycle", level: .info) { fields in
            XCTAssertEqual(fields["_visibility_state"], "hidden")
        }
    }

    func testMakeLoggingActionOmitsVisibilityStateWhenAbsent() throws {
        try givenLifecycleMessage(visibilityState: nil)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.lifecycle", level: .info) { fields in
            XCTAssertNil(fields["_visibility_state"])
        }
    }

    func testMakeLoggingActionIncludesPageViewParentSpan() throws {
        let javascriptID = UUID().uuidString
        let nativeID = UUID()
        try givenLifecycleMessage(parentSpanId: javascriptID)

        let action = whenMakingLoggingAction(
            context: WebViewLoggingContext(
                currentPageViewSpanID: nil,
                nativePageViewSpanIDs: [javascriptID: nativeID]
            )
        )

        assertWebLogAction(action, message: "webview.lifecycle", level: .info) { fields in
            XCTAssertEqual(fields["_span_parent_id"], nativeID.uuidString)
        }
    }
}

private extension LifecycleMessageTests {
    func givenLifecycleMessage(
        event: String = "load",
        performanceTime: Double = 100,
        visibilityState: String? = nil,
        parentSpanId: String? = nil
    ) throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "lifecycle",
            "timestamp": 1700000000000,
            "parentSpanId": \(parentSpanId.map { "\"\($0)\"" } ?? "null"),
            "event": "\(event)",
            "performanceTime": \(performanceTime),
            "visibilityState": \(visibilityState.map { "\"\($0)\"" } ?? "null")
        }
        """
        sut = try decodeWebViewMessage(LifecycleMessage.self, from: json)
    }

    func whenMakingLoggingAction(context: WebViewLoggingContext = .empty) -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: context)
    }
}
