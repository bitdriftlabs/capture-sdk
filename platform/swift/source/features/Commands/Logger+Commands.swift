// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

extension Logger {
    /// Registers an asynchronous command that can be invoked from a live session or workflow.
    ///
    /// - parameter key:     The command identifier.
    ///
    /// - parameter handler: The asynchronous application handler for this command.
    ///
    /// - returns: A handle that can be used to unregister this command.
    @discardableResult
    public static func registerCommand(
        key: String,
        handler: @escaping CommandHandler
    ) async throws -> CommandHandle {
        guard let logger = Self.getShared() as? Logger else {
            throw CommandRegistrationError.loggerNotStarted
        }
        _ = try await logger.commandRegistry.register(key: key, handler: handler)
        logger.underlyingLogger.registerCommand(key: key, target: logger.commandsTarget)
        return CommandHandle { [weak logger] in
            await logger?.unregisterCommand(key: key)
        }
    }

    /// Unregisters a previously registered command. Calling this for an unknown key is a no-op.
    ///
    /// - parameter key: The command identifier to unregister.
    public static func unregisterCommand(key: String) async {
        await (Self.getShared() as? Logger)?.unregisterCommand(key: key)
    }

    func executeCommand(
        key: String,
        arguments: CommandArguments
    ) async -> Result<CommandResult, CommandError> {
        await commandRegistry.execute(key: key, arguments: arguments)
    }

    private func unregisterCommand(key: String) async {
        underlyingLogger.unregisterCommand(key: key)
        await commandRegistry.unregister(key: key)
    }
}
