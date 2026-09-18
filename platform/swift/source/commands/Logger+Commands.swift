// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension Logger {
    /// Registers a command that may be executed repeatedly until its handle is unregistered.
    @discardableResult
    public static func registerCommand(
        key: String,
        handler: @escaping ([String], @escaping (Result<CommandResult, CommandError>) -> Void) -> Void
    ) -> CommandHandle {
        guard let logger = Self.getShared() as? Logger else {
            return CommandHandle(unregisterHandler: {})
        }

        return logger.commandRegistry.register(key: key, handler: handler)
    }

    /// Removes the command currently registered for `key`.
    public static func unregisterCommand(key: String) {
        (Self.getShared() as? Logger)?.commandRegistry.unregister(key: key)
    }

    static func executeCommand(
        key: String,
        arguments: [String],
        completion: @escaping CommandCompletion
    ) {
        guard let logger = Self.getShared() as? Logger else {
            completion(.failure(CommandError(title: "Logger not started")))
            return
        }

        logger.commandRegistry.execute(key: key, arguments: arguments, completion: completion)
    }
}
