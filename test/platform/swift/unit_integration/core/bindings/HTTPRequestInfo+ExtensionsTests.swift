// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class HTTPRequestInfoExtensionsTests: XCTestCase {
    func testInitWithURLRequest() throws {
        let url = try XCTUnwrap(URL(string: "https://foo.com/ping?test=1"))
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringCacheData, timeoutInterval: 15.0)
        request.httpMethod = "POST"

        let requestInfo = HTTPRequestInfo(urlRequest: request)

        XCTAssertEqual("foo.com", requestInfo.host)
        XCTAssertEqual("/ping", requestInfo.path?.value)
        XCTAssertEqual("POST", requestInfo.method)
        XCTAssertEqual("test=1", requestInfo.query)
    }

    func testInitWithDataTask() throws {
        let url = try XCTUnwrap(URL(string: "https://foo.com/ping?test=1"))
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringCacheData, timeoutInterval: 15.0)
        request.httpMethod = "POST"

        let task = URLSession.shared.dataTask(with: request)

        let requestInfo = try XCTUnwrap(HTTPRequestInfo(task: task))
        XCTAssertEqual("foo.com", requestInfo.host)
        XCTAssertEqual("/ping", requestInfo.path?.value)
        XCTAssertEqual("POST", requestInfo.method)
        XCTAssertEqual("test=1", requestInfo.query)
    }
}
