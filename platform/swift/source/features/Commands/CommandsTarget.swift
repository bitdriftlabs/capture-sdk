// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class CommandsTarget: NSObject {
    private enum ArgumentType: UInt {
        case string
        case binary
        case uint64
        case double
        case int64
        case bool
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
        } catch {
            complete(requestID: requestID, result: .failure(CommandError(title: "Invalid command arguments")))
            return
        }

        Task { [registry] in
            let result = await registry.execute(key: key as String, arguments: parsedArguments)
            complete(requestID: requestID, result: result)
        }
    }

    private func parseArguments(_ arguments: NSArray) throws -> CommandArguments {
        var result = CommandArguments()
        for case let argument as NSDictionary in arguments {
            guard let name = argument["name"] as? String,
                  let rawType = argument["type"] as? NSNumber,
                  let type = ArgumentType(rawValue: rawType.uintValue),
                  let value = argument["value"]
            else {
                throw ArgumentParsingError.invalidArgument
            }
            result[name] = try parseArgument(type: type, value: value)
        }
        guard result.count == arguments.count else {
            throw ArgumentParsingError.invalidArgument
        }
        return result
    }

    private func parseArgument(type: ArgumentType, value: Any) throws -> CommandArgument {
        switch type {
        case .string:
            guard let value = value as? String else { throw ArgumentParsingError.invalidArgument }
            return .string(value)
        case .binary:
            guard let value = value as? Data else { throw ArgumentParsingError.invalidArgument }
            return .binary(value)
        case .uint64:
            guard let value = value as? NSNumber else { throw ArgumentParsingError.invalidArgument }
            return .uint64(value.uint64Value)
        case .double:
            guard let value = value as? NSNumber else { throw ArgumentParsingError.invalidArgument }
            return .double(value.doubleValue)
        case .int64:
            guard let value = value as? NSNumber else { throw ArgumentParsingError.invalidArgument }
            return .int64(value.int64Value)
        case .bool:
            guard let value = value as? NSNumber else { throw ArgumentParsingError.invalidArgument }
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
                message
            )
        }
    }
}

private enum ArgumentParsingError: Error {
    case invalidArgument
}
