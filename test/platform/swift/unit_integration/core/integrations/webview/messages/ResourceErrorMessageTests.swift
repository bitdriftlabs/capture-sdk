// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class ResourceErrorMessageTests: XCTestCase {
    private var sut: ResourceErrorMessage!

    func testMakeLoggingActionLogsAtWarningLevel() throws {
        try givenResourceErrorMessage(
            resourceType: "image",
            url: "https://example.com/missing.png",
            tagName: "img"
        )
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.resourceError", level: .warning) { fields in
            XCTAssertEqual(fields["_resource_type"], "image")
            XCTAssertEqual(fields["_url"], "https://example.com/missing.png")
            XCTAssertEqual(fields["_tag_name"], "img")
        }
    }
}

private extension ResourceErrorMessageTests {
    func givenResourceErrorMessage(resourceType: String, url: String, tagName: String) throws {
        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "resourceError",
            "timestamp": 1700000000000,
            "resourceType": "\(resourceType)",
            "url": "\(url)",
            "tagName": "\(tagName)"
        }
        """
        sut = try decodeWebViewMessage(ResourceErrorMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
