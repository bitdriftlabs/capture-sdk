// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.txt

@testable import Capture
import XCTest

final class CommandErrorTests: XCTestCase {
    func testPredefinedErrorsHaveStandardTitles() {
        XCTAssertEqual(CommandError.notFound.title, "Command not found")
        XCTAssertEqual(CommandError.alreadyExecuting.title, "Command is already executing")
        XCTAssertEqual(CommandError.maximumConcurrency.title, "Maximum command concurrency reached")
    }

    func testPredefinedErrorsHaveTheirExpectedCodes() {
        XCTAssertEqual(CommandError.notFound.code, "command_unknown")
        XCTAssertEqual(CommandError.alreadyExecuting.code, "command_already_executing")
        XCTAssertEqual(CommandError.maximumConcurrency.code, "max_command_concurrency")
    }

    func testInvalidArgumentsRetainsItsDescription() {
        let error = CommandError.invalidArguments(description: "Argument 'verbose' is missing a value.")

        XCTAssertEqual(error.title, "Invalid command arguments")
        XCTAssertEqual(error.description, "Argument 'verbose' is missing a value.")
        XCTAssertEqual(error.code, "invalid_arguments")
    }

    func testCustomErrorRetainsItsDetails() {
        let error = CommandError(title: "Capture failed", context: ["operation": "capture"])

        XCTAssertEqual(error.title, "Capture failed")
        XCTAssertEqual(error.context, ["operation": "capture"])
        XCTAssertEqual(error.code, "handler_failed")
    }
}
