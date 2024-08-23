// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureTestBridge
import Foundation
import XCTest

final class ErrorLoggerTest: XCTestCase {
    // Verifies that we are able to correctly pass a string from Rust to Swift. Strings in Rust
    // are not null terminated, so care must be taken to null terminate the string before passing
    // it over the C boundary. The Swift end can then convert the null terminated C string into a
    // Swift String.
    //
    // To test this, we set up a mock error reporter which we pass to the Rust side. This will wrap
    // this NSObject with SwiftErrorReporter, which is responsible for converting the Rust slice
    // into an appropriate C string pointer, which is then provided to us via the MockErrorReporter.
    // As the slice on the Rust end is a substring, it is guaranteed to not be null terminated, which
    // means that us receiving the expected substring means that SwiftErrorReporter did its job and
    // null terminated the data before passing it back up to Swift.
    // TODO(snowp): Evolve this into a full integration test that validates that we actually send out
    // the error string via URLSession.
    func testErrorString() {
        let errorExpectation = self.expectation(description: "Error callback was called")
        let reporter = MockRemoteErrorReporter()
        reporter.onReportError = { msg in
            XCTAssertEqual(msg, "a")
            errorExpectation.fulfill()
        }

        // Pass the reporter to Rust, which will leverage SwiftErrorReporter to deliver the error message
        // via the mock reporter we provide.
        test_null_termination(Unmanaged.passUnretained(reporter).toOpaque())

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [errorExpectation], timeout: 0.1))
    }
}
