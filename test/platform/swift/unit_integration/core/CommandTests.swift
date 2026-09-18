// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class CommandTests: XCTestCase {
    override func setUpWithError() throws {
        Logger.resetShared(logger: try Logger.testLogger())
    }

    override func tearDown() {
        Logger.resetShared()
        super.tearDown()
    }

    func testAsyncCommandCanExecuteRepeatedly() async {
        let registration = Logger.registerCommand(key: "print_memory") { arguments in
            .success(CommandResult(context: ["argument_count": String(arguments.count)]))
        }
        defer { registration.unregister() }

        let first = await Logger.executeCommand(key: "print_memory", arguments: [])
        let second = await Logger.executeCommand(key: "print_memory", arguments: ["detailed"])

        guard case .success = first else {
            return XCTFail("Expected the first command execution to succeed")
        }
        guard case .success(let secondResult) = second else {
            return XCTFail("Expected the second command execution to succeed")
        }
        XCTAssertEqual("1", secondResult.context["argument_count"])
    }

    func testAsyncCommandCanSuspendBeforeCompleting() async {
        let registration = Logger.registerCommand(key: "async_memory") { _ in
            try? await Task.sleep(nanoseconds: 1_000_000)
            return .success(CommandResult(context: ["completed": "asynchronously"]))
        }
        defer { registration.unregister() }

        let result = await Logger.executeCommand(key: "async_memory", arguments: [])
        guard case .success(let commandResult) = result else {
            return XCTFail("Expected command execution to succeed")
        }
        XCTAssertEqual("asynchronously", commandResult.context["completed"])
    }

    func testUnknownCommandReturnsDescriptiveError() async {
        let result = await Logger.executeCommand(key: "missing", arguments: [])
        guard case .failure(let error) = result else {
            return XCTFail("Expected command execution to fail")
        }
        XCTAssertEqual("Command not found", error.title)
    }

    func testUnregisterPreventsFutureExecutions() async {
        let registration = Logger.registerCommand(key: "print_memory") { _ in .success(CommandResult()) }
        registration.unregister()
        let result = await Logger.executeCommand(key: "print_memory", arguments: [])
        XCTAssertFailure(result)
    }

    func testUnregisterCommandWithKeyPreventsFutureExecutions() async {
        _ = Logger.registerCommand(key: "memory_by_key") { _ in .success(CommandResult()) }

        Logger.unregisterCommand(key: "memory_by_key")

        let result = await Logger.executeCommand(key: "memory_by_key", arguments: [])
        XCTAssertFailure(result)
    }

    func testOlderHandleUnregistersCurrentCommand() async {
        let first = Logger.registerCommand(key: "memory") { _ in
            .success(CommandResult(context: ["source": "first"]))
        }
        Logger.registerCommand(key: "memory") { _ in
            .success(CommandResult(context: ["source": "second"]))
        }

        first.unregister()

        let result = await Logger.executeCommand(key: "memory", arguments: [])
        XCTAssertFailure(result)
    }

    private func XCTAssertFailure(
        _ result: Result<CommandResult, CommandError>,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard case .failure = result else {
            return XCTFail("Expected command execution to fail", file: file, line: line)
        }
    }
}
