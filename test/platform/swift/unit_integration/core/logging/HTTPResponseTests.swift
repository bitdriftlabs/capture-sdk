// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class HTTPResponseTests: XCTestCase {
    func testServerStatusCodeIsCaptured() {
        let httpURLResponse = HTTPURLResponse(url: URL(staticString: "https://google.com"), statusCode: 200,
                                              httpVersion: nil, headerFields: nil)

        let response = HTTPResponse(httpURLResponse: httpURLResponse, error: nil)

        XCTAssertEqual(.success, response.result)
        XCTAssertEqual(200, response.statusCode)
        XCTAssertNil(response.error)
    }

    func testClientSideCancellationTakesPrecedenceOverServerSideSuccess() {
        let httpURLResponse = HTTPURLResponse(url: URL(staticString: "https://google.com"), statusCode: 200,
                                              httpVersion: nil, headerFields: nil)

        let canceledError = NSError(domain: "io.bitdrift.capture.logger.error", code: NSURLErrorCancelled)
        let response = HTTPResponse(httpURLResponse: httpURLResponse, error: canceledError)

        XCTAssertEqual(.canceled, response.result)
        XCTAssertEqual(200, response.statusCode)
        XCTAssertEqual(canceledError, response.error as NSError?)
    }

    func testClientSideCancellationTakesPrecedenceOverServerSideFailure() {
        let httpURLResponse = HTTPURLResponse(url: URL(staticString: "https://google.com"), statusCode: 200,
                                              httpVersion: nil, headerFields: nil)

        let error = NSError(domain: "io.bitdrift.capture.logger.error", code: NSURLErrorCannotOpenFile)
        let response = HTTPResponse(httpURLResponse: httpURLResponse, error: error)

        XCTAssertEqual(.failure, response.result)
        XCTAssertEqual(200, response.statusCode)
        XCTAssertEqual(error, response.error as NSError?)
    }
}
