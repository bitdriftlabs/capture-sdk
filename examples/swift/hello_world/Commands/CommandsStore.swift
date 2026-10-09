// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Foundation

actor CommandsStore {
    private var flags: [String: Bool] = [:]
    private var invocationsByKey: [String: [CommandInvocationRecord]] = [:]

    func execute(key: String, arguments: CommandArguments) -> Result<CommandResult, CommandError> {
        let result: Result<CommandResult, CommandError>

        switch key {
        case "memory_dump":
            let isAttachment: Bool
            if case let .bool(value) = arguments["is_attachment"] {
                isAttachment = value
            } else {
                isAttachment = false
            }
            result = MemoryDump.capture().flatMap { memoryDump in
                memoryDump.commandResult(isAttachment: isAttachment)
            }
        case "flip_flag":
            guard let flag = arguments.string(for: "flag"), !flag.isEmpty else {
                result = .failure(CommandError(title: "Missing flag argument"))
                break
            }

            let previous = self.flags[flag] ?? false
            let current = !previous
            self.flags[flag] = current
            result = .success(
                CommandResult(context: [
                    "flag": flag,
                    "from": String(previous),
                    "to": String(current),
                ])
            )
        default:
            result = .failure(CommandError(title: "Unknown command"))
        }

        let outcome: CommandInvocationRecord.Outcome
        switch result {
        case let .success(commandResult):
            outcome = .succeeded(context: commandResult.context)
        case let .failure(error):
            outcome = .failed(message: [error.title, error.description].compactMap { $0 }.joined(separator: ": "))
        }
        let record = CommandInvocationRecord(
            id: UUID(),
            receivedAt: Date(),
            arguments: arguments.mapValues(\.displayValue),
            outcome: outcome
        )
        self.invocationsByKey[key, default: []].insert(record, at: 0)
        return result
    }

    func invocations(for key: String) -> [CommandInvocationRecord] {
        self.invocationsByKey[key, default: []]
    }
}
