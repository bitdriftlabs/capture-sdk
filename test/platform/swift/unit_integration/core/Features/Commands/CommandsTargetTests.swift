// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class CommandsTargetTests: XCTestCase {
    private var sut: CommandsTarget!

    override func setUp() {
        sut = CommandsTarget(registry: CommandRegistry())
    }

    func testParsingArgumentsReportsMissingArgumentName() {
        let arguments: NSArray = [[
            "type": NSNumber(value: 5),
            "value": NSNumber(value: true),
        ]]

        let error = whenParsingArguments(arguments)

        thenErrorHasDescription(error, "Argument at index 0 is missing a name.")
    }

    func testParsingArgumentsReportsUnsupportedArgumentType() {
        let arguments: NSArray = [[
            "name": "is_attachment",
            "type": NSNumber(value: 99),
            "value": NSNumber(value: true),
        ]]

        let error = whenParsingArguments(arguments)

        thenErrorHasDescription(error, "Argument 'is_attachment' has an unsupported type.")
    }

    func testParsingArgumentsReportsTypeMismatch() {
        let arguments: NSArray = [[
            "name": "is_attachment",
            "type": NSNumber(value: 5),
            "value": "true",
        ]]

        let error = whenParsingArguments(arguments)

        thenErrorHasDescription(
            error,
            "Argument 'is_attachment' has a value that does not match its declared bool type."
        )
    }
}

private extension CommandsTargetTests {
    func whenParsingArguments(_ arguments: NSArray) -> Error {
        do {
            _ = try sut.parseArguments(arguments)
            XCTFail("Expected argument parsing to fail")
            return NSError(domain: "CommandsTargetTests", code: 0)
        } catch {
            return error
        }
    }

    func thenErrorHasDescription(_ error: Error, _ expectedDescription: String) {
        XCTAssertEqual(error.localizedDescription, expectedDescription)
    }
}
