// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class CommandsTarget: NSObject, @unchecked Sendable {
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

    init(registry: CommandRegistry) {
        self.registry = registry
    }

    @objc(executeCommand:arguments:requestID:)
    func executeCommand(_ key: NSString, arguments: NSArray, requestID: UInt64) {
        let parsedArguments: CommandArguments
        do {
            parsedArguments = try parseArguments(arguments)
        } catch let error as ArgumentParsingError {
            complete(
                requestID: requestID,
                result: .failure(CommandError.invalidArguments(description: error.errorDescription))
            )
            return
        } catch {
            complete(requestID: requestID, result: .failure(CommandError.invalidArguments()))
            return
        }

        Task { [registry] in
            let result = await registry.execute(key: key as String, arguments: parsedArguments)
            complete(requestID: requestID, result: result)
        }
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

    private func complete(requestID: UInt64, result: Result<CommandResult, CommandError>) {
        switch result {
        case let .success(result):
            capture_complete_command(
                requestID,
                true,
                result.context,
                result.attachment?.data,
                result.attachment?.mimeType,
                result.attachment?.filename,
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
                nil,
                error.code,
                message
            )
        }
    }
}
