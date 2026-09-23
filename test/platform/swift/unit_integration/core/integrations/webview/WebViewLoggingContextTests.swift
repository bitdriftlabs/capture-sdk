// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class WebViewLoggingContextTests: XCTestCase {
    func testParentSpanIDWithExplicitUUIDReturnsIt() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: nil)
        let uuid = UUID()

        XCTAssertEqual(sut.parentSpanID(for: uuid.uuidString), uuid)
    }

    func testParentSpanIDWithInvalidExplicitIDReturnsNil() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: nil)

        XCTAssertNil(sut.parentSpanID(for: "not-a-uuid"))
    }

    func testParentSpanIDWithNoExplicitIDUsesCurrentPageViewUUID() {
        let uuid = UUID()
        let sut = WebViewLoggingContext(currentPageViewSpanID: uuid.uuidString)

        XCTAssertEqual(sut.parentSpanID(for: nil), uuid)
    }

    func testParentSpanIDWithNoExplicitOrCurrentPageViewReturnsNil() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: nil)

        XCTAssertNil(sut.parentSpanID(for: nil))
    }

    func testParentSpanIDWithInvalidCurrentPageViewReturnsNil() {
        let sut = WebViewLoggingContext(currentPageViewSpanID: "not-a-uuid")

        XCTAssertNil(sut.parentSpanID(for: nil))
    }
}
