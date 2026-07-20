// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class UserInteractionMessageTests: XCTestCase {
    private var sut: UserInteractionMessage!

    func testMakeLoggingActionWithClickLogsAtDebugLevel() throws {
        try givenUserInteractionMessage(interactionType: "click", isClickable: true)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.userInteraction", level: .debug)
    }

    func testMakeLoggingActionWithRageClickLogsAtWarningLevel() throws {
        try givenUserInteractionMessage(
            interactionType: "rageClick",
            isClickable: true,
            clickCount: 5,
            timeWindowMs: 1_000
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.userInteraction", level: .warning) { fields in
            XCTAssertEqual(fields["_click_count"], "5")
            XCTAssertEqual(fields["_time_window_ms"], "1000.0")
        }
    }

    func testMakeLoggingActionIncludesTagNameAndClickability() throws {
        try givenUserInteractionMessage(tagName: "button", isClickable: true)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.userInteraction", level: .debug) { fields in
            XCTAssertEqual(fields["_tag_name"], "button")
            XCTAssertEqual(fields["_is_clickable"], "true")
        }
    }

    func testMakeLoggingActionIncludesElementIdClassNameAndTextContentWhenPresent() throws {
        try givenUserInteractionMessage(
            elementId: "submit-btn",
            className: "btn btn-primary",
            textContent: "Submit"
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.userInteraction", level: .debug) { fields in
            XCTAssertEqual(fields["_element_id"], "submit-btn")
            XCTAssertEqual(fields["_class_name"], "btn btn-primary")
            XCTAssertEqual(fields["_text_content"], "Submit")
        }
    }

    func testMakeLoggingActionForNonRageClickOmitsClickCountAndTimeWindow() throws {
        try givenUserInteractionMessage(interactionType: "click", clickCount: nil, timeWindowMs: nil)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.userInteraction", level: .debug) { fields in
            XCTAssertNil(fields["_click_count"])
            XCTAssertNil(fields["_time_window_ms"])
        }
    }
}

private extension UserInteractionMessageTests {
    func givenUserInteractionMessage(
        interactionType: String = "click",
        tagName: String = "div",
        elementId: String? = nil,
        className: String? = nil,
        textContent: String? = nil,
        isClickable: Bool = false,
        clickCount: Int? = nil,
        timeWindowMs: Double? = nil,
        duration: Double? = nil
    ) throws {
        let elementIdJSON: String = elementId.map { "\"\($0)\"" } ?? "null"
        let classNameJSON: String = className.map { "\"\($0)\"" } ?? "null"
        let textContentJSON: String = textContent.map { "\"\($0)\"" } ?? "null"
        let clickCountJSON: String = clickCount.map { String($0) } ?? "null"
        let timeWindowMsJSON: String = timeWindowMs.map { String($0) } ?? "null"
        let durationJSON: String = duration.map { String($0) } ?? "null"
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "userInteraction",
            "timestamp": 1700000000000,
            "interactionType": "\(interactionType)",
            "tagName": "\(tagName)",
            "elementId": \(elementIdJSON),
            "className": \(classNameJSON),
            "textContent": \(textContentJSON),
            "isClickable": \(isClickable),
            "clickCount": \(clickCountJSON),
            "timeWindowMs": \(timeWindowMsJSON),
            "duration": \(durationJSON)
        }
        """
        sut = try decodeWebViewMessage(UserInteractionMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
