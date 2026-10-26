// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
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
        ], ]

        let error = whenParsingArguments(arguments)

        thenErrorHasDescription(error, "Argument at index 0 is missing a name.")
    }

    func testParsingArgumentsReportsUnsupportedArgumentType() {
        let arguments: NSArray = [[
            "name": "is_attachment",
            "type": NSNumber(value: 99),
            "value": NSNumber(value: true),
        ], ]

        let error = whenParsingArguments(arguments)

        thenErrorHasDescription(error, "Argument 'is_attachment' has an unsupported type.")
    }

    func testParsingArgumentsReportsTypeMismatch() {
        let arguments: NSArray = [[
            "name": "is_attachment",
            "type": NSNumber(value: 5),
            "value": "true",
        ], ]

        let error = whenParsingArguments(arguments)

        thenErrorHasDescription(
            error,
            "Argument 'is_attachment' has a value that does not match its declared bool type."
        )
    }

    func testCancellationReachesHandlerTaskAndSuppressesLateResultWithoutReleasingClaimsEarly() async throws {
        let started = expectation(description: "handler started")
        let cancelled = expectation(description: "handler task cancelled")
        let gate = CommandExecutionGate()
        let sawCancellation = CommandTestValue(false)
        let completions = CommandTestValue([UInt64]())
        let registry = CommandRegistry(commands: [
            Command(key: "first") { _ in
                started.fulfill()
                await withTaskCancellationHandler {
                    await gate.wait()
                } onCancel: {
                    cancelled.fulfill()
                }
                sawCancellation.set(Task.isCancelled)
                return .success(CommandResult(context: ["late": "result"]))
            },
            Command(key: "second") { _ in .success(CommandResult()) },
        ], maxConcurrentExecutions: 1)
        sut = CommandsTarget(registry: registry) { requestID, _ in
            completions.append(requestID)
        }
        XCTAssertTrue(sut.responds(to: NSSelectorFromString("cancelCommand:")))
        sut.executeCommand("first", arguments: [], requestID: 1)
        await fulfillment(of: [started])
        let task = try XCTUnwrap(sut.executionTask(for: 1))

        sut.cancelCommand(999)
        sut.cancelCommand(1)
        sut.cancelCommand(1)
        await fulfillment(of: [cancelled])

        let sameKey = await registry.execute(key: "first", arguments: [:])
        let otherKey = await registry.execute(key: "second", arguments: [:])
        XCTAssertEqual(sameKey, .failure(.alreadyExecuting))
        XCTAssertEqual(otherKey, .failure(.maximumConcurrency))
        await gate.open()
        await task.value

        XCTAssertTrue(sawCancellation.value)
        XCTAssertEqual(completions.value, [])
        XCTAssertNil(sut.executionTask(for: 1))
        let next = await registry.execute(key: "second", arguments: [:])
        XCTAssertEqual(next, .success(CommandResult()))
    }

    func testCancelCommandReturnsBeforeCancellationHandlersFinish() async throws {
        let started = expectation(description: "handler started")
        let cancellationFinished = expectation(description: "cancellation handler finished")
        let release = DispatchSemaphore(value: 0)
        let gate = CommandExecutionGate()
        let handlerFinished = CommandTestValue(false)
        let registry = CommandRegistry(commands: [
            Command(key: "first") { _ in
                started.fulfill()
                await withTaskCancellationHandler {
                    await gate.wait()
                } onCancel: {
                    _ = release.wait(timeout: .now() + 5)
                    handlerFinished.set(true)
                    cancellationFinished.fulfill()
                }
                return .success(CommandResult())
            },
        ])
        sut = CommandsTarget(registry: registry) { _, _ in }
        sut.executeCommand("first", arguments: [], requestID: 1)
        await fulfillment(of: [started])
        let task = try XCTUnwrap(sut.executionTask(for: 1))

        sut.cancelCommand(1)

        XCTAssertFalse(handlerFinished.value)
        release.signal()
        await fulfillment(of: [cancellationFinished])
        await gate.open()
        await task.value
    }

    func testCancellationBeforeRegistryEntryNeverInvokesHandlerOrPublishesResult() async throws {
        let held = expectation(description: "registry executor held")
        let barrier = CommandRegistryBarrier(held: held)
        let invoked = CommandTestValue(false)
        let completed = CommandTestValue(false)
        let registry = CommandRegistry(commands: [
            Command(key: "first") { _ in
                invoked.set(true)
                return .success(CommandResult())
            },
        ], maxConcurrentExecutions: 1)
        sut = CommandsTarget(registry: registry) { _, _ in completed.set(true) }
        let holdingTask = Task { await registry.holdExecutor(barrier) }
        await fulfillment(of: [held])
        defer { barrier.open() }

        sut.executeCommand("first", arguments: [], requestID: 1)
        let task = try XCTUnwrap(sut.executionTask(for: 1))
        sut.cancelCommand(1)
        await waitUntilCancelled(task)
        barrier.open()
        await holdingTask.value
        await task.value

        XCTAssertFalse(invoked.value)
        XCTAssertFalse(completed.value)
        XCTAssertNil(sut.executionTask(for: 1))
        let next = await registry.execute(key: "first", arguments: [:])
        XCTAssertEqual(next, .success(CommandResult()))
    }

    func testImmediateResultsRemoveRegisteredTasksAndCompleteExactlyOnce() async {
        let completed = expectation(description: "all requests completed")
        completed.expectedFulfillmentCount = 32
        let completions = CommandTestValue([UInt64]())
        sut = CommandsTarget(registry: CommandRegistry(commands: [
            Command(key: "first") { _ in .success(CommandResult()) },
        ])) { requestID, _ in
            completions.append(requestID)
            completed.fulfill()
        }

        for requestID in UInt64(1) ... 32 {
            sut.executeCommand("first", arguments: [], requestID: requestID)
        }
        await fulfillment(of: [completed])

        XCTAssertEqual(completions.value.sorted(), Array(UInt64(1) ... 32))
        for requestID in UInt64(1) ... 32 {
            XCTAssertNil(sut.executionTask(for: requestID))
            sut.cancelCommand(requestID)
        }
        XCTAssertEqual(completions.value.count, 32)
    }

    func testRegistryRejectsNoncooperativeSuccessAfterCancellationAndReleasesClaimsOnExit() async {
        let started = expectation(description: "handler started")
        let gate = CommandExecutionGate()
        let registry = CommandRegistry(commands: [
            Command(key: "first") { _ in
                started.fulfill()
                await gate.wait()
                return .success(CommandResult())
            },
            Command(key: "second") { _ in .success(CommandResult()) },
        ], maxConcurrentExecutions: 1)
        let task = Task { await registry.execute(key: "first", arguments: [:]) }
        await fulfillment(of: [started])

        task.cancel()
        let busy = await registry.execute(key: "first", arguments: [:])
        XCTAssertEqual(busy, .failure(.alreadyExecuting))
        await gate.open()
        let result = await task.value

        XCTAssertEqual(result, .failure(CommandError(title: "Command cancelled")))
        let next = await registry.execute(key: "second", arguments: [:])
        XCTAssertEqual(next, .success(CommandResult()))
    }

    func testUnacceptedFileCompletionsAreDeletedForAppsAndBuiltins() async throws {
        // Deliberately outside WellKnownCommandType to exercise a future SDK-owned built-in.
        let futureBuiltinType: Int32 = 4711
        for builtinType: Int32? in [nil, futureBuiltinType] {
            let file = try makeAttachmentFile()
            defer { try? FileManager.default.removeItem(at: file) }
            let started = expectation(description: "file handler started")
            let gate = CommandExecutionGate()
            let command = Command(key: "file") { _ in
                started.fulfill()
                await gate.wait()
                return .success(CommandResult(attachment: .file(url: file, mimeType: "application/octet-stream")))
            }
            let registry = CommandRegistry(
                commands: [command],
                builtins: [.init(type: futureBuiltinType, command: command, artifactType: "file")]
            )
            let target = CommandsTarget(registry: registry, builtinType: builtinType)
            // This request ID has no native pending invocation, so the bridge rejects its result.
            target.executeCommand("file", arguments: [], requestID: UInt64.max)
            await fulfillment(of: [started])
            let task = try XCTUnwrap(target.executionTask(for: UInt64.max))
            await gate.open()
            await task.value
            XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
        }
    }

    func testFileReturnedAfterCancellationIsDeletedWithoutCompleting() async throws {
        let file = try makeAttachmentFile()
        defer { try? FileManager.default.removeItem(at: file) }
        let started = expectation(description: "file handler started")
        let cancelled = expectation(description: "file handler cancelled")
        let gate = CommandExecutionGate()
        let completed = CommandTestValue(false)
        sut = CommandsTarget(registry: CommandRegistry(commands: [Command(key: "file") { _ in
            started.fulfill()
            await withTaskCancellationHandler {
                await gate.wait()
            } onCancel: {
                cancelled.fulfill()
            }
            return .success(CommandResult(attachment: .file(url: file, mimeType: "application/octet-stream")))
        }, ])) { _, _ in completed.set(true) }
        sut.executeCommand("file", arguments: [], requestID: 1)
        await fulfillment(of: [started])
        let task = try XCTUnwrap(sut.executionTask(for: 1))
        sut.cancelCommand(1)
        await fulfillment(of: [cancelled])
        await gate.open()
        await task.value
        XCTAssertFalse(completed.value)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }
}

private extension CommandsTargetTests {
    func makeAttachmentFile() throws -> URL {
        let documents = try XCTUnwrap(FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first)
        let directory = documents.appendingPathComponent("command-test-files", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent(UUID().uuidString)
        try Data([1, 2, 3]).write(to: file)
        return file
    }

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

    func waitUntilCancelled(_ task: Task<Void, Never>) async {
        while !task.isCancelled {
            await Task.yield()
        }
    }
}

private actor CommandExecutionGate {
    private var continuation: CheckedContinuation<Void, Never>?
    private var isOpen = false

    func wait() async {
        guard !isOpen else { return }
        await withCheckedContinuation { continuation = $0 }
    }

    func open() {
        isOpen = true
        continuation?.resume()
        continuation = nil
    }
}

private final class CommandTestValue<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: Value

    init(_ value: Value) {
        stored = value
    }

    var value: Value {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    func set(_ value: Value) {
        lock.lock()
        defer { lock.unlock() }
        stored = value
    }

    func append(_ requestID: UInt64) where Value == [UInt64] {
        lock.lock()
        defer { lock.unlock() }
        stored.append(requestID)
    }
}

private final class CommandRegistryBarrier: @unchecked Sendable {
    private let condition = NSCondition()
    private let held: XCTestExpectation
    private var isOpen = false

    init(held: XCTestExpectation) {
        self.held = held
    }

    func wait() {
        condition.lock()
        defer { condition.unlock() }
        held.fulfill()
        while !isOpen {
            condition.wait()
        }
    }

    func open() {
        condition.lock()
        defer { condition.unlock() }
        isOpen = true
        condition.broadcast()
    }
}

private extension CommandRegistry {
    func holdExecutor(_ barrier: CommandRegistryBarrier) {
        barrier.wait()
    }
}
