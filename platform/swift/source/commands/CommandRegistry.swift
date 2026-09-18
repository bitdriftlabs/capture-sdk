// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

typealias CommandHandler = ([String]) async -> Result<CommandResult, CommandError>

final class CommandRegistry: @unchecked Sendable {
    private let lock = Lock()
    private var handlers = [String: CommandHandler]()

    func register(key: String, handler: @escaping CommandHandler) -> CommandHandle {
        self.lock.withLock {
            self.handlers[key] = handler
        }

        return CommandHandle { [weak self] in
            self?.unregister(key: key)
        }
    }

    func execute(key: String, arguments: [String]) async -> Result<CommandResult, CommandError> {
        let handler = self.lock.withLock { self.handlers[key] }
        guard let handler else {
            return .failure(CommandError(title: "Command not found"))
        }
        return await handler(arguments)
    }

    func unregister(key: String) {
        _ = self.lock.withLock {
            self.handlers.removeValue(forKey: key)
        }
    }
}
