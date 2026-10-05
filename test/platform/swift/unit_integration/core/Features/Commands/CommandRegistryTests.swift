// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class CommandRegistryTests: XCTestCase {
    private var sut: CommandRegistry!

    override func setUp() {
        sut = CommandRegistry()
    }

    func testRegisteringCommandExecutesHandlerWithNamedArguments() async throws {
        await givenRegisteredCommand(key: "flip_flag") { arguments in
            .success(CommandResult(context: ["flag": arguments.string(for: "flag") ?? ""]))
        }

        let result = await whenExecutingCommand(key: "flip_flag", arguments: ["flag": .string("dark_mode")])

        try thenResultIsSuccess(result, context: ["flag": "dark_mode"])
    }

    func testInitialCommandsAreAvailableBeforeAnyDynamicRegistration() async throws {
        sut = CommandRegistry(commands: [
            Command(key: "memory_dump") { _ in .success(CommandResult(context: ["source": "startup"])) }
        ])

        let result = await whenExecutingCommand(key: "memory_dump")

        try thenResultIsSuccess(result, context: ["source": "startup"])
    }

    func testInitialCommandsKeepTheFirstHandlerForDuplicateKeys() async throws {
        sut = CommandRegistry(commands: [
            Command(key: "memory_dump") { _ in .success(CommandResult(context: ["source": "first"])) },
            Command(key: "memory_dump") { _ in .success(CommandResult(context: ["source": "second"])) },
        ])

        let result = await whenExecutingCommand(key: "memory_dump")

        try thenResultIsSuccess(result, context: ["source": "first"])
    }

    func testRegisteringDuplicateCommandFailsWithoutReplacingOriginalHandler() async throws {
        await givenRegisteredCommand(key: "memory") { _ in
            .success(CommandResult(context: ["source": "first"]))
        }

        let error = await whenRegisteringDuplicateCommand(key: "memory")
        let result = await whenExecutingCommand(key: "memory")

        thenErrorIsDuplicateRegistration(error, key: "memory")
        try thenResultIsSuccess(result, context: ["source": "first"])
    }

    func testExecutionsOfTheSameCommandDoNotOverlap() async throws {
        let firstExecutionStarted = expectation(description: "first execution started")
        let allowFirstExecutionToFinish = AsyncGate()
        let maximumConcurrentExecutions = LockedValue(0)
        let currentConcurrentExecutions = LockedValue(0)
        let invocationCount = LockedValue(0)

        await givenRegisteredCommand(key: "serial") { _ in
            let invocation = invocationCount.modify { value in
                value += 1
                return value
            }
            let current = currentConcurrentExecutions.modify { value in
                value += 1
                return value
            }
            maximumConcurrentExecutions.modify { value in
                value = max(value, current)
            }
            if invocation == 1 {
                firstExecutionStarted.fulfill()
                await allowFirstExecutionToFinish.wait()
            }
            currentConcurrentExecutions.modify { $0 -= 1 }
            return .success(CommandResult())
        }

        async let first = whenExecutingCommand(key: "serial")
        await fulfillment(of: [firstExecutionStarted])
        async let second = whenExecutingCommand(key: "serial")

        whenOpeningGate(allowFirstExecutionToFinish)

        try thenResultIsSuccess(await first)
        try thenResultIsSuccess(await second)
        thenMaximumConcurrentExecutionsIs(maximumConcurrentExecutions, expected: 1)
    }

    func testExecutionsOfDifferentCommandsCanOverlap() async throws {
        let executionsStarted = expectation(description: "both executions started")
        executionsStarted.expectedFulfillmentCount = 2
        let allowExecutionsToFinish = AsyncGate()
        let currentConcurrentExecutions = LockedValue(0)
        let maximumConcurrentExecutions = LockedValue(0)

        let handler: CommandHandler = { _ in
            let current = currentConcurrentExecutions.modify { value in
                value += 1
                return value
            }
            maximumConcurrentExecutions.modify { value in
                value = max(value, current)
            }
            executionsStarted.fulfill()
            await allowExecutionsToFinish.wait()
            currentConcurrentExecutions.modify { $0 -= 1 }
            return .success(CommandResult())
        }
        await givenRegisteredCommand(key: "first", handler: handler)
        await givenRegisteredCommand(key: "second", handler: handler)

        async let first = whenExecutingCommand(key: "first")
        async let second = whenExecutingCommand(key: "second")
        await fulfillment(of: [executionsStarted])

        whenOpeningGate(allowExecutionsToFinish)

        try thenResultIsSuccess(await first)
        try thenResultIsSuccess(await second)
        thenMaximumConcurrentExecutionsIs(maximumConcurrentExecutions, expected: 2)
    }

    func testUnregisteringCommandCancelsQueuedExecutionsWithoutInterruptingActiveExecution() async throws {
        let firstExecutionStarted = expectation(description: "first execution started")
        let allowFirstExecutionToFinish = AsyncGate()
        let handle = await givenRegisteredCommand(key: "serial") { _ in
            firstExecutionStarted.fulfill()
            await allowFirstExecutionToFinish.wait()
            return .success(CommandResult())
        }

        async let activeResult = whenExecutingCommand(key: "serial")
        await fulfillment(of: [firstExecutionStarted])
        async let queuedResult = whenExecutingCommand(key: "serial")
        await whenExecutionIsQueued(for: "serial")

        await whenUnregistering(handle)
        whenOpeningGate(allowFirstExecutionToFinish)

        try thenResultIsSuccess(await activeResult)
        thenResultIsUnregistered(await queuedResult)
    }

    func testUnregisteringCommandByKeyPreventsFutureExecutions() async {
        await givenRegisteredCommand(key: "memory") { _ in .success(CommandResult()) }

        await whenUnregisteringCommand(key: "memory")
        let result = await whenExecutingCommand(key: "memory")

        thenResultIsCommandNotFound(result)
    }
}

private extension CommandRegistryTests {
    @discardableResult
    func givenRegisteredCommand(key: String, handler: @escaping CommandHandler) async -> CommandHandle {
        try! await sut.register(key: key, handler: handler)
    }

    func whenRegisteringDuplicateCommand(key: String) async -> Error {
        do {
            _ = try await sut.register(key: key) { _ in .success(CommandResult()) }
            XCTFail("Expected duplicate command registration to fail")
            return CommandRegistrationError.duplicateKey(key)
        } catch {
            return error
        }
    }

    func whenExecutingCommand(
        key: String,
        arguments: CommandArguments = [:]
    ) async -> Result<CommandResult, CommandError> {
        await sut.execute(key: key, arguments: arguments)
    }

    func whenUnregistering(_ handle: CommandHandle) async {
        await handle.unregister()
    }

    func whenUnregisteringCommand(key: String) async {
        await sut.unregister(key: key)
    }

    func whenExecutionIsQueued(for key: String) async {
        for _ in 0 ..< 100 {
            if await sut.queuedExecutionCount(for: key) == 1 {
                return
            }
            await Task.yield()
        }
        XCTFail("Expected execution to be queued")
    }

    func whenOpeningGate(_ gate: AsyncGate) {
        gate.open()
    }

    func thenResultIsSuccess(
        _ result: Result<CommandResult, CommandError>,
        context: [String: String] = [:],
        file: StaticString = #filePath,
        line: UInt = #line
    ) throws {
        let commandResult = try result.get()
        XCTAssertEqual(commandResult.context, context, file: file, line: line)
    }

    func thenErrorIsDuplicateRegistration(_ error: Error, key: String) {
        XCTAssertEqual(error as? CommandRegistrationError, .duplicateKey(key))
    }

    func thenResultIsUnregistered(_ result: Result<CommandResult, CommandError>) {
        XCTAssertEqual(result, .failure(.unregistered))
    }

    func thenResultIsCommandNotFound(_ result: Result<CommandResult, CommandError>) {
        XCTAssertEqual(result, .failure(.notFound))
    }

    func thenMaximumConcurrentExecutionsIs(_ value: LockedValue<Int>, expected: Int) {
        XCTAssertEqual(value.value, expected)
    }

    func thenInvocationCountIs(_ value: LockedValue<Int>, expected: Int) {
        XCTAssertEqual(value.value, expected)
    }
}

private final class LockedValue<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var underlyingValue: Value

    init(_ value: Value) {
        underlyingValue = value
    }

    var value: Value {
        lock.lock()
        defer { lock.unlock() }
        return underlyingValue
    }

    @discardableResult
    func modify<Result>(_ body: (inout Value) -> Result) -> Result {
        lock.lock()
        defer { lock.unlock() }
        return body(&underlyingValue)
    }
}

private actor AsyncGate {
    private var continuations = [CheckedContinuation<Void, Never>]()
    private var isOpen = false

    func wait() async {
        guard !isOpen else {
            return
        }
        await withCheckedContinuation { continuations.append($0) }
    }

    nonisolated func open() {
        Task { await self.openFromTask() }
    }

    private func openFromTask() {
        isOpen = true
        continuations.forEach { $0.resume() }
        continuations.removeAll()
    }
}
