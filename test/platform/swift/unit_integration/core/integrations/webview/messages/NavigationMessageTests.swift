// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class NavigationMessageTests: XCTestCase {
    private var sut: NavigationMessage!

    func testMakeLoggingActionLogsNavigationAtDebugLevel() throws {
        try givenNavigationMessage(
            fromUrl: "https://example.com/a",
            toUrl: "https://example.com/b",
            method: "pushState"
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.navigation", level: .debug) { fields in
            XCTAssertEqual(fields["_fromUrl"], "https://example.com/a")
            XCTAssertEqual(fields["_toUrl"], "https://example.com/b")
            XCTAssertEqual(fields["_method"], "pushState")
        }
    }
}

private extension NavigationMessageTests {
    func givenNavigationMessage(fromUrl: String, toUrl: String, method: String) throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "navigation",
            "timestamp": 1700000000000,
            "fromUrl": "\(fromUrl)",
            "toUrl": "\(toUrl)",
            "method": "\(method)"
        }
        """
        sut = try decodeWebViewMessage(NavigationMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
