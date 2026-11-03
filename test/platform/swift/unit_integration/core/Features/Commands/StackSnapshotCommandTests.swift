// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
import CaptureMocks
import Foundation
import XCTest

final class StackSnapshotCommandTests: XCTestCase {
    func testRegisteredByDefaultBeforeLoggerStarts() async throws {
        let bridge = MockLoggerBridging()
        let logger = try Logger.testLogger(
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        XCTAssertEqual(bridge.commandRegistrationEvents, ["register:ios_stack_snapshot", "start"])
        let result = await logger.executeCommand(key: "ios_stack_snapshot", arguments: [:])
        guard case let .failure(error) = result else { return XCTFail("expected invalid arguments") }
        XCTAssertEqual(error.code, CommandError.invalidArguments().code)
        withExtendedLifetime(logger) {}
    }

    func testCancelledCommandDoesNotCapture() async {
        let command = StackSnapshotCommand.make()
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return await command.handler(["bufferSizeKb": .uint64(256)])
        }
        guard case let .failure(error) = await task.value else { return XCTFail("expected cancellation") }
        XCTAssertEqual(error.title, "Command cancelled")
    }

    func testRejectsInvalidArguments() async {
        let command = StackSnapshotCommand.make()
        let invalid: [CommandArguments] = [
            [:], ["bufferSizeKb": .uint64(0)], ["bufferSizeKb": .int64(-1)],
            ["bufferSizeKb": .uint64(16385)], ["bufferSizeKb": .string("256")],
            ["bufferSizeKb": .double(256)],
            ["bufferSizeKb": .uint64(256), "extra": .bool(true)],
        ]
        for arguments in invalid {
            let result = await command.handler(arguments)
            guard case let .failure(error) = result else { return XCTFail("expected invalid arguments") }
            XCTAssertEqual(error.code, CommandError.invalidArguments().code)
        }
    }

    func testCapturesFileAttachmentWithThreadStacks() async throws {
        let command = StackSnapshotCommand.make()
        #if !arch(arm64)
        let result = await command.handler(["bufferSizeKb": .uint64(256)])
        guard case let .failure(error) = result else { return XCTFail("expected unsupported architecture") }
        XCTAssertEqual(error.title, "stack snapshot requires ARM64")
        return
        #else
        let result = try await command.handler(["bufferSizeKb": .uint64(256)]).get()
        let attachment = try XCTUnwrap(result.attachment)
        defer { attachment.discardOwnedFile() }
        XCTAssertEqual(attachment.mimeType, "application/json")
        let url = try XCTUnwrap(attachment.fileURL)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let threads = try XCTUnwrap(json["threads"] as? [[String: Any]])
        XCTAssertFalse(threads.isEmpty)
        XCTAssertTrue(threads.contains { thread in
            guard let samples = thread["samples"] as? [String: Any],
                  let data = samples["data"] as? [[Any]] else { return false }
            return !data.isEmpty
        })
        #endif
    }
}
