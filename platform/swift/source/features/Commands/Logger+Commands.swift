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
    /// - returns: A handle that can be used to unregister this command, or `nil` if the logger has not started.
    @discardableResult
    public static func registerCommand(
        key: String,
        handler: @escaping CommandHandler
    ) async -> CommandHandle? {
        guard let logger = Self.getShared() as? Logger else {
            return nil
        }
        return await logger.commandRegistry.register(
            key: key,
            handler: handler,
            target: logger.commandsTarget
        )
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
        await commandRegistry.unregister(key: key)
    }
}
