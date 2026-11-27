// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI)
@testable import Capture
import CaptureLoggerBridge
import CaptureMocks
import Foundation
import XCTest

final class StackSnapshotCommandTests: XCTestCase {
    func testBridgeCreatedBeforeMainThreadRegistrationExportsItsLateIdentity() async throws {
        // Reproduce background startup deterministically: construct the bridge while the shared
        // identity is unknown, then register the main thread before using that same bridge.
        capture_stack_set_main_thread_id(0)
        let bridge = StackCaptureBridge()
        await MainActor.run {
            StackCaptureBridge.prepareMainThreadID()
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        try await Task.detached {
            for profile in [false, true] {
                if profile {
                    _ = try bridge.profile(
                        frequencyHz: 20, durationMs: 100, bufferSizeKb: 2048, to: url
                    ).get()
                } else {
                    _ = try bridge.capture(bufferSizeKb: 2048, to: url).get()
                }
                let bytes = try Data(contentsOf: url)
                XCTAssertNotNil(bytes.range(of: Data("Main Thread".utf8)))
                try FileManager.default.removeItem(at: url)
            }
        }.value
    }

    func testSummarySeparatesMissingUserContextFromFailedThreads() {
        let summary = StackSnapshotSummary(
            capturedThreads: 1, failedThreads: 2, truncatedThreads: 0, skippedThreads: 1,
            noUserContextThreads: 3
        )
        XCTAssertEqual(summary.context["failed_threads"], "2")
        XCTAssertEqual(summary.context["no_user_context_threads"], "3")
    }

    func testRegisteredByDefaultBeforeLoggerStarts() async throws {
        let bridge = MockLoggerBridging()
        let logger = try Logger.testLogger(
            loggerBridgingFactoryProvider: MockLoggerBridgingFactory(logger: bridge)
        )
        XCTAssertEqual(
            bridge.commandRegistrationEvents,
            ["register:ios_stack_snapshot", "register:ios_stack_profile", "start"]
        )
        let result = await logger.executeCommand(key: "ios_stack_snapshot", arguments: [:])
        guard case let .failure(error) = result else {
            return XCTFail("expected invalid arguments")
        }
        XCTAssertEqual(error.code, CommandError.invalidArguments().code)
        withExtendedLifetime(logger) {}
    }

    func testCancelledCommandDoesNotCapture() async {
        let command = StackSnapshotCommand().make()
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return await command.handler(["buffer_size_kb": .uint64(256)])
        }
        guard case let .failure(error) = await task.value else {
            return XCTFail("expected cancellation")
        }
        XCTAssertEqual(error.message, "cancelled")
    }

    func testCancellationWhileWorkerRunsSignalsTheNativeBridge() async {
        let started = expectation(description: "worker started")
        let finish = DispatchSemaphore(value: 0)
        let nativeBridge = StackCaptureBridge()
        let bridge = SnapshotBridgeStub(capture: { size, url in
            started.fulfill()
            finish.wait()
            let result = nativeBridge.capture(bufferSizeKb: size, to: url)
            guard case let .failure(error) = result else {
                XCTFail("expected cancelled native bridge")
                return result
            }
            XCTAssertEqual(error.message, "cancelled")
            XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
            return result
        }, cancel: { nativeBridge.cancel() })
        let command = StackSnapshotCommand(makeBridge: { bridge }).make()
        let task = Task { await command.handler(["buffer_size_kb": .uint64(256)]) }
        await fulfillment(of: [started], timeout: 5)
        task.cancel()
        finish.signal()
        guard case let .failure(error) = await task.value else {
            return XCTFail("expected cancellation")
        }
        XCTAssertEqual(error.message, "cancelled")
    }

    func testConcurrentCommandInstancesRejectBeforeCreatingBridgeUntilCancelledWorkerFinishes() async {
        let started = expectation(description: "worker started")
        let cancelled = expectation(description: "bridge cancelled")
        let finish = DispatchSemaphore(value: 0)
        defer { finish.signal() }
        let bridge = SnapshotBridgeStub(capture: { _, _ in
            started.fulfill()
            finish.wait()
            return .failure(CommandError(message: "worker_finished"))
        }, cancel: { cancelled.fulfill() })
        let first = StackSnapshotCommand(makeBridge: { bridge }).make()
        let rejected = StackSnapshotCommand(makeBridge: {
            XCTFail("a busy request must not construct its bridge")
            return bridge
        }).make()
        let arguments: CommandArguments = ["buffer_size_kb": .uint64(256)]
        let task = Task { await first.handler(arguments) }
        await fulfillment(of: [started], timeout: 5)
        for cancel in [false, true] {
            if cancel {
                task.cancel()
                await fulfillment(of: [cancelled], timeout: 5)
            }
            let result = await rejected.handler(arguments)
            guard case let .failure(error) = result else {
                finish.signal()
                _ = await task.value
                return XCTFail("expected busy rejection")
            }
            XCTAssertEqual(error.code, CommandError.alreadyExecuting.code)
        }
        finish.signal()
        guard case let .failure(error) = await task.value else {
            return XCTFail("expected cancellation")
        }
        XCTAssertEqual(error.message, "cancelled")

        let nextBridge = SnapshotBridgeStub(capture: { _, _ in
            .failure(CommandError(message: "capture_failed"))
        })
        let next = StackSnapshotCommand(makeBridge: { nextBridge }).make()
        // Both cancellation and ordinary failure must release the slot for the next request.
        for _ in 0 ..< 2 {
            guard case let .failure(error) = await next.handler(arguments) else {
                return XCTFail("expected fixture capture failure")
            }
            XCTAssertEqual(error.message, "capture_failed")
        }
    }

    func testAttachmentReturnedAfterCancellationIsDeleted() async throws {
        let started = expectation(description: "worker started")
        let finish = DispatchSemaphore(value: 0)
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let fileManager = SnapshotFileManager(directory: directory)
        let bridge = SnapshotBridgeStub(capture: { _, url in
            XCTAssertEqual(url.deletingLastPathComponent().path, directory.path)
            started.fulfill()
            finish.wait()
            do {
                try Data("late result".utf8).write(to: url)
            } catch { return .failure(CommandError(message: "fixture could not be written")) }
            return .success(StackSnapshotSummary(
                capturedThreads: 1, failedThreads: 0, truncatedThreads: 0, skippedThreads: 1,
                noUserContextThreads: 0
            ))
        })
        let command = StackSnapshotCommand(fileManager: fileManager, makeBridge: { bridge }).make()
        let task = Task { await command.handler(["buffer_size_kb": .uint64(256)]) }
        await fulfillment(of: [started], timeout: 5)
        task.cancel()
        finish.signal()
        guard case let .failure(error) = await task.value else {
            return XCTFail("expected cancellation")
        }
        XCTAssertEqual(error.message, "cancelled")
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
        XCTAssertEqual(fileManager.removals, 1)
    }

    func testRejectsInvalidArguments() async {
        let command = StackSnapshotCommand().make()
        let invalid: [CommandArguments] = [
            [:], ["bufferSizeKb": .uint64(256)], ["buffer_size_kb": .uint64(0)],
            ["buffer_size_kb": .int64(-1)],
            ["buffer_size_kb": .uint64(UInt64(UInt32.max) + 1)], ["buffer_size_kb": .string("256")],
            ["buffer_size_kb": .double(256)],
            ["buffer_size_kb": .uint64(256), "extra": .bool(true)],
        ]
        for arguments in invalid {
            let result = await command.handler(arguments)
            guard case let .failure(error) = result else {
                return XCTFail("expected invalid arguments")
            }
            XCTAssertEqual(error.code, CommandError.invalidArguments().code)
        }
    }

    func testAcceptsPositiveBudgetsWithoutAnArbitraryRange() async {
        let bridge = SnapshotBridgeStub(capture: { size, _ in
            .failure(CommandError(message: String(size)))
        })
        let command = StackSnapshotCommand(makeBridge: { bridge }).make()
        for size: UInt64 in [1, 15, 16385, UInt64(UInt32.max)] {
            guard case let .failure(error) = await command.handler(["buffer_size_kb": .uint64(size)]) else {
                return XCTFail("expected the bridge fixture to return the accepted size")
            }
            XCTAssertEqual(error.message, String(size))
        }
    }

    func testCapturesFileAttachmentWithThreadStacks() async throws {
        let command = StackSnapshotCommand().make()
        #if !arch(arm64)
        let result = await command.handler(["buffer_size_kb": .uint64(256)])
        guard case let .failure(error) = result else {
            return XCTFail("expected unsupported architecture")
        }
        XCTAssertEqual(error.message, "unsupported")
        return
        #else
        let result = try await command.handler(["buffer_size_kb": .uint64(256)]).get()
        let attachment = try XCTUnwrap(result.attachment)
        defer { attachment.discardOwnedFile() }
        XCTAssertEqual(attachment.mimeType, "application/vnd.bitdrift.thread-snapshot")
        let url = try XCTUnwrap(attachment.fileURL)
        XCTAssertEqual(url.pathExtension, "pb")
        let bytes = try Data(contentsOf: url)
        XCTAssertFalse(bytes.isEmpty)
        XCTAssertThrowsError(try JSONSerialization.jsonObject(with: bytes))
        let captured = try XCTUnwrap(result.context["captured_threads"].flatMap(Int.init))
        XCTAssertGreaterThan(captured, 0)
        XCTAssertNotNil(result.context["no_user_context_threads"])
        XCTAssertNotNil(result.context["failed_threads"])
        XCTAssertNotNil(result.context["truncated_threads"])
        XCTAssertNotNil(result.context["skipped_threads"])
        #endif
    }
}

private final class SnapshotBridgeStub: StackSnapshotBridging, @unchecked Sendable {
    private let onCapture: @Sendable (UInt32, URL) -> Result<StackSnapshotSummary, CommandError>
    private let onCancel: @Sendable () -> Void

    init(
        capture: @escaping @Sendable (UInt32, URL) -> Result<StackSnapshotSummary, CommandError>,
        cancel: @escaping @Sendable () -> Void = {}
    ) {
        self.onCapture = capture
        self.onCancel = cancel
    }

    func capture(bufferSizeKb: UInt32, to url: URL) -> Result<StackSnapshotSummary, CommandError> {
        onCapture(bufferSizeKb, url)
    }

    func cancel() { onCancel() }
}

private final class SnapshotFileManager: FileManager, @unchecked Sendable {
    private let directory: URL
    private(set) var removals = 0

    init(directory: URL) {
        self.directory = directory
        super.init()
    }

    override var temporaryDirectory: URL { directory }

    override func removeItem(at url: URL) throws {
        removals += 1
        try super.removeItem(at: url)
    }
}
