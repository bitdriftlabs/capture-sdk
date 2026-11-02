// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

@_spi(ExperimentalAPI) @testable import Capture
import XCTest

final class CommandArgumentTests: XCTestCase {
    func testValueUnboxing() {
        for value: any CommandArgumentConvertible in [UInt64(3089), Int64(-8), Double(84.7), "abc", false, true, "oh no".data(using: .utf8)!] {
            XCTContext.runActivity(named: "unbox \(value.self)-type value") { _ in
                AssertAnyEqual(value.asArgument().value(), value)
            }
        }
    }
}
