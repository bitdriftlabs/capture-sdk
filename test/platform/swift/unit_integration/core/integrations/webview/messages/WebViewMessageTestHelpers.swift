// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

extension WebViewLoggingContext {
    static let empty = WebViewLoggingContext(currentPageViewSpanID: nil, activePageViewSpans: [:])
}

extension XCTestCase {
    func decodeWebViewMessage<T: Decodable>(_ type: T.Type, from json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }

    func assertWebLogAction(
        _ action: WebViewLoggingAction?,
        message expectedMessage: String,
        level expectedLevel: LogLevel,
        file: StaticString = #filePath,
        line: UInt = #line,
        fields assertFields: ([String: String]) -> Void = { _ in }
    ) {
        guard case let .log(level, message, fields)? = action else {
            XCTFail("expected .log action, got \(String(describing: action))", file: file, line: line)
            return
        }

        XCTAssertEqual(level, expectedLevel, file: file, line: line)
        XCTAssertEqual(message, expectedMessage, file: file, line: line)
        assertFields((fields as? [String: String]) ?? [:])
    }
}
