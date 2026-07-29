// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureLoggerBridge
@testable import CaptureMocks
import XCTest

final class WebViewLoggingContextTests: XCTestCase {
    func testParentLoggerSpanIDWithActiveSpanMatchingWebViewSpanIDReturnsItsID() {
        let span = makeSpan()
        let sut = WebViewLoggingContext(
            currentPageViewSpanID: nil,
            activePageViewSpans: ["webview-span": span]
        )

        XCTAssertEqual(sut.parentLoggerSpanID(for: "webview-span"), span.id)
    }

    func testParentLoggerSpanIDWithUnknownWebViewSpanIDThatIsAValidUUIDFallsBackToParsingIt() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: nil, activePageViewSpans: [:])
        let uuid = UUID()

        XCTAssertEqual(sut.parentLoggerSpanID(for: uuid.uuidString), uuid)
    }

    func testParentLoggerSpanIDWithUnknownWebViewSpanIDThatIsNotAUUIDReturnsNil() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: nil, activePageViewSpans: [:])

        XCTAssertNil(sut.parentLoggerSpanID(for: "not-a-uuid"))
    }

    func testParentLoggerSpanIDWithNilWebViewSpanIDFallsBackToCurrentPageViewSpan() {
        let span = makeSpan()
        let sut = WebViewLoggingContext(
            currentPageViewSpanID: "current-span",
            activePageViewSpans: ["current-span": span]
        )

        XCTAssertEqual(sut.parentLoggerSpanID(for: nil), span.id)
    }

    func testParentLoggerSpanIDWithNilWebViewSpanIDAndNoCurrentPageViewReturnsNil() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: nil, activePageViewSpans: [:])

        XCTAssertNil(sut.parentLoggerSpanID(for: nil))
    }

    func testParentLoggerSpanIDWithNilWebViewSpanIDAndUnknownCurrentPageViewReturnsNil() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: "missing-span", activePageViewSpans: [:])

        XCTAssertNil(sut.parentLoggerSpanID(for: nil))
    }
}

private extension WebViewLoggingContextTests {
    func makeSpan() -> Span {
        Span(
            logger: MockCoreLogging(),
            name: "test-span",
            level: .debug,
            file: nil,
            line: nil,
            function: nil,
            fields: nil,
            timeProvider: MockTimeProvider(),
            customStartTimeInterval: nil,
            parentSpanID: nil
        )
    }
}
