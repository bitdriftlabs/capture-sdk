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

    func testCommandCanExecuteRepeatedly() {
        let registration = Logger.registerCommand(key: "print_memory") { arguments, complete in
            complete(.success(CommandResult(context: ["argument_count": String(arguments.count)])))
        }
        defer { registration.unregister() }

        self.assertCommandResult(key: "print_memory", arguments: [], isSuccess: true)
        self.assertCommandResult(key: "print_memory", arguments: ["detailed"], isSuccess: true)
    }

    func testCommandCanCompleteAsynchronously() {
        let registration = Logger.registerCommand(key: "async_memory") { _, complete in
            DispatchQueue.global().async {
                complete(.success(CommandResult(context: ["completed": "asynchronously"])))
            }
        }
        defer { registration.unregister() }

        let expectation = self.expectation(description: "asynchronous command result")
        Logger.executeCommand(key: "async_memory", arguments: []) { result in
            guard case .success(let commandResult) = result else {
                return XCTFail("Expected command execution to succeed")
            }
            XCTAssertEqual("asynchronously", commandResult.context["completed"])
            expectation.fulfill()
        }
        XCTAssertEqual(.completed, XCTWaiter.wait(for: [expectation], timeout: 1))
    }

    func testUnknownCommandReturnsDescriptiveError() {
        let expectation = self.expectation(description: "unknown command result")
        Logger.executeCommand(key: "missing", arguments: []) { result in
            guard case .failure(let error) = result else {
                return XCTFail("Expected command execution to fail")
            }
            XCTAssertEqual("Command not found", error.title)
            expectation.fulfill()
        }
        XCTAssertEqual(.completed, XCTWaiter.wait(for: [expectation], timeout: 1))
    }

    func testUnregisterPreventsFutureExecutions() {
        let registration = Logger.registerCommand(key: "print_memory") { _, complete in
            complete(.success(CommandResult()))
        }

        registration.unregister()

        self.assertCommandResult(key: "print_memory", arguments: [], isSuccess: false)
    }

    func testUnregisterCommandWithKeyPreventsFutureExecutions() {
        _ = Logger.registerCommand(key: "memory_by_key") { _, complete in
            complete(.success(CommandResult()))
        }

        Logger.unregisterCommand(key: "memory_by_key")

        self.assertCommandResult(key: "memory_by_key", arguments: [], isSuccess: false)
    }

    func testOlderHandleUnregistersCurrentCommand() {
        let first = Logger.registerCommand(key: "memory") { _, complete in
            complete(.success(CommandResult(context: ["source": "first"])))
        }
        Logger.registerCommand(key: "memory") { _, complete in
            complete(.success(CommandResult(context: ["source": "second"])))
        }

        first.unregister()

        self.assertCommandResult(key: "memory", arguments: [], isSuccess: false)
    }

    private func assertCommandResult(key: String, arguments: [String], isSuccess: Bool) {
        let expectation = self.expectation(description: "command result")
        Logger.executeCommand(key: key, arguments: arguments) { result in
            switch result {
            case .success:
                XCTAssertTrue(isSuccess)
            case .failure:
                XCTAssertFalse(isSuccess)
            }
            expectation.fulfill()
        }
        XCTAssertEqual(.completed, XCTWaiter.wait(for: [expectation], timeout: 1))
    }
}
