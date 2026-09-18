// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension Logger {
    @discardableResult
    public static func registerCommand(
        key: String,
        handler: @escaping ([String]) async -> Result<CommandResult, CommandError>
    ) -> CommandHandle {
        guard let logger = Self.getShared() as? Logger else {
            return CommandHandle(unregisterHandler: {})
        }

        return logger.commandRegistry.register(key: key, handler: handler)
    }

    public static func unregisterCommand(key: String) {
        (Self.getShared() as? Logger)?.commandRegistry.unregister(key: key)
    }

    static func executeCommand(
        key: String,
        arguments: [String]
    ) async -> Result<CommandResult, CommandError> {
        guard let logger = Self.getShared() as? Logger else {
            return .failure(CommandError(title: "Logger not started"))
        }

        return await logger.commandRegistry.execute(key: key, arguments: arguments)
    }
}
