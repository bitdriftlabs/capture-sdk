// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class CommandsTarget: NSObject, @unchecked Sendable {
    private struct Execution {
        let task: Task<Void, Never>
        var cancelled = false
    }

    private enum ArgumentType: UInt {
        case string
        case binary
        case uint64
        case double
        case int64
        case bool

        var description: String {
            switch self {
            case .string:
                "string"
            case .binary:
                "binary"
            case .uint64:
                "uint64"
            case .double:
                "double"
            case .int64:
                "int64"
            case .bool:
                "bool"
            }
        }
    }

    private enum ArgumentParsingError: LocalizedError {
        case notAnObject(index: Int)
        case missingName(index: Int)
        case unsupportedType(name: String)
        case missingValue(name: String)
        case valueTypeMismatch(name: String, type: ArgumentType)

        var errorDescription: String? {
            switch self {
            case let .notAnObject(index):
                "Argument at index \(index) is not an object."
            case let .missingName(index):
                "Argument at index \(index) is missing a name."
            case let .unsupportedType(name):
                "Argument '\(name)' has an unsupported type."
            case let .missingValue(name):
                "Argument '\(name)' is missing a value."
            case let .valueTypeMismatch(name, type):
                "Argument '\(name)' has a value that does not match its declared \(type.description) type."
            }
        }
    }

    private let registry: CommandRegistry
    private let lock = Lock()
    private var executions = [UInt64: Execution]()
    private let completion: @Sendable (UInt64, Result<CommandResult, CommandError>) -> Void

    init(
        registry: CommandRegistry,
        completion: @escaping @Sendable (UInt64, Result<CommandResult, CommandError>) -> Void = {
            CommandsTarget.complete(requestID: $0, result: $1)
        }
    ) {
        self.registry = registry
        self.completion = completion
    }

    @objc(executeCommand:arguments:requestID:)
    func executeCommand(_ key: NSString, arguments: NSArray, requestID: UInt64) {
        let parsedArguments: CommandArguments
        do {
            parsedArguments = try parseArguments(arguments)
        } catch let error as ArgumentParsingError {
            completion(
                requestID,
                .failure(CommandError.invalidArguments(description: error.errorDescription))
            )
            return
        } catch {
            completion(requestID, .failure(CommandError.invalidArguments()))
            return
        }

        // The task must cross this lock before entering the registry or completing. This makes
        // publication of its handle atomic with both cancellation and an immediate result.
        lock.withLock {
            let task = Task { [self, registry] in
                guard canExecute(requestID: requestID), !Task.isCancelled else {
                    finish(requestID: requestID, result: nil)
                    return
                }
                let result = await registry.execute(key: key as String, arguments: parsedArguments)
                finish(requestID: requestID, result: result)
            }
            executions[requestID] = Execution(task: task)
        }
    }

    @objc(cancelCommand:)
    func cancelCommand(_ requestID: UInt64) {
        let task = lock.withLock {
            executions[requestID]?.cancelled = true
            return executions[requestID]?.task
        }
        guard let task else { return }
        // Task cancellation runs application cancellation handlers; never run them under our lock
        // or on the calling Rust thread.
        DispatchQueue.global().async { task.cancel() }
    }

    func executionTask(for requestID: UInt64) -> Task<Void, Never>? {
        lock.withLock { executions[requestID]?.task }
    }

    private func canExecute(requestID: UInt64) -> Bool {
        lock.withLock { executions[requestID]?.cancelled == false }
    }

    private func finish(requestID: UInt64, result: Result<CommandResult, CommandError>?) {
        let execution = lock.withLock { executions.removeValue(forKey: requestID) }
        guard let execution, !execution.cancelled, !Task.isCancelled, let result else {
            return
        }
        completion(requestID, result)
    }

    func parseArguments(_ arguments: NSArray) throws -> CommandArguments {
        var result = CommandArguments()
        for (index, rawArgument) in arguments.enumerated() {
            guard let argument = rawArgument as? NSDictionary else {
                throw ArgumentParsingError.notAnObject(index: index)
            }
            guard let name = argument["name"] as? String else {
                throw ArgumentParsingError.missingName(index: index)
            }
            guard let rawType = argument["type"] as? NSNumber,
                  let type = ArgumentType(rawValue: rawType.uintValue)
            else {
                throw ArgumentParsingError.unsupportedType(name: name)
            }
            guard let value = argument["value"] else {
                throw ArgumentParsingError.missingValue(name: name)
            }
            result[name] = try parseArgument(name: name, type: type, value: value)
        }
        return result
    }

    private func parseArgument(name: String, type: ArgumentType, value: Any) throws -> CommandArgument {
        switch type {
        case .string:
            guard let value = value as? String else {
                throw ArgumentParsingError.valueTypeMismatch(name: name, type: type)
            }
            return .string(value)
        case .binary:
            guard let value = value as? Data else {
                throw ArgumentParsingError.valueTypeMismatch(name: name, type: type)
            }
            return .binary(value)
        case .uint64:
            guard let value = value as? NSNumber else {
                throw ArgumentParsingError.valueTypeMismatch(name: name, type: type)
            }
            return .uint64(value.uint64Value)
        case .double:
            guard let value = value as? NSNumber else {
                throw ArgumentParsingError.valueTypeMismatch(name: name, type: type)
            }
            return .double(value.doubleValue)
        case .int64:
            guard let value = value as? NSNumber else {
                throw ArgumentParsingError.valueTypeMismatch(name: name, type: type)
            }
            return .int64(value.int64Value)
        case .bool:
            guard let value = value as? NSNumber else {
                throw ArgumentParsingError.valueTypeMismatch(name: name, type: type)
            }
            return .bool(value.boolValue)
        }
    }

    private static func complete(requestID: UInt64, result: Result<CommandResult, CommandError>) {
        switch result {
        case let .success(result):
            capture_complete_command(
                requestID,
                true,
                result.context,
                result.attachment?.data,
                result.attachment?.mimeType,
                nil,
                nil
            )
        case let .failure(error):
            let message = [error.title, error.description].compactMap { $0 }.joined(separator: ": ")
            capture_complete_command(
                requestID,
                false,
                error.context,
                nil,
                nil,
                error.code,
                message
            )
        }
    }
}
