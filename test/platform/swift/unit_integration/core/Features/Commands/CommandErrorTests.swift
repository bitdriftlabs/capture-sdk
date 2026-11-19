// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
import XCTest

final class CommandErrorTests: XCTestCase {
    func testPredefinedErrorsHaveStandardMessages() {
        XCTAssertEqual(CommandError.notFound.message, "Command not found")
        XCTAssertEqual(CommandError.alreadyExecuting.message, "Command is already executing")
        XCTAssertEqual(CommandError.maximumConcurrency.message, "Maximum command concurrency reached")
    }

    func testPredefinedErrorsHaveTheirExpectedCodes() {
        XCTAssertEqual(CommandError.notFound.code, "command_unknown")
        XCTAssertEqual(CommandError.alreadyExecuting.code, "command_already_executing")
        XCTAssertEqual(CommandError.maximumConcurrency.code, "max_command_concurrency")
    }

    func testInvalidArgumentsUsesTheSuppliedMessageOrADefault() {
        let error = CommandError.invalidArguments(message: "Argument 'verbose' is missing a value.")

        XCTAssertEqual(CommandError.invalidArguments().message, "Invalid command arguments")
        XCTAssertEqual(error.message, "Argument 'verbose' is missing a value.")
        XCTAssertEqual(error.code, "invalid_arguments")
    }

    func testCustomErrorRetainsItsDetails() {
        let error = CommandError(message: "Capture failed: Rate limited", context: ["error": "process limit"])

        XCTAssertEqual(error.message, "Capture failed: Rate limited")
        XCTAssertEqual(error.context, ["error": "process limit"])
        XCTAssertEqual(error.code, "handler_failed")
    }
}
