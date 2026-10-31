// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

actor CommandRegistry {
    struct Builtin: Sendable {
        let type: Int32
        let command: Command
        let artifactType: String
        var timeoutMilliseconds: Int64?
    }

    private enum Identity: Hashable {
        case app(String)
        case builtin(Int32)
    }

    private final class Registration: @unchecked Sendable {
        let handler: CommandHandler

        init(handler: @escaping CommandHandler) {
            self.handler = handler
        }
    }

    private let bridge: (any CommandRegistrationBridging)?
    private let maxConcurrentExecutions: Int
    private var registrations = [Identity: Registration]()
    private var executingKeys = Set<Identity>()
    private var activeExecutionCount = 0

    init(
        commands: [Command] = [],
        builtins: [Builtin] = [],
        bridge: (any CommandRegistrationBridging)? = nil,
        maxConcurrentExecutions: Int = 10
    ) {
        self.bridge = bridge
        self.maxConcurrentExecutions = maxConcurrentExecutions
        for command in commands {
            registrations[.app(command.key)] = Registration(handler: command.handler)
        }
        for builtin in builtins {
            registrations[.builtin(builtin.type)] = Registration(handler: builtin.command.handler)
        }
    }

    func register(
        key: String,
        handler: @escaping CommandHandler,
        target: CommandsTarget
    ) -> CommandHandle {
        let registration = Registration(handler: handler)
        let identity = Identity.app(key)
        if registrations[identity] != nil {
            registrations[identity] = registration
        } else {
            registrations[identity] = registration
            bridge?.registerCommand(key: key, target: target)
        }
        return CommandHandle { [weak self, weak registration] in
            guard let registration else { return }
            _ = await self?.unregister(key: key, matching: registration)
        }
    }

    func execute(
        key: String,
        arguments: CommandArguments,
        builtinType: Int32? = nil
    ) async -> Result<CommandResult, CommandError> {
        guard !Task.isCancelled else {
            return .failure(CommandError(title: "Command cancelled"))
        }
        let identity = builtinType.map(Identity.builtin) ?? .app(key)
        guard let registration = registrations[identity] else {
            return .failure(.notFound)
        }
        guard executingKeys.insert(identity).inserted else {
            return .failure(.alreadyExecuting)
        }
        guard activeExecutionCount < maxConcurrentExecutions else {
            executingKeys.remove(identity)
            return .failure(.maximumConcurrency)
        }
        activeExecutionCount += 1
        defer {
            activeExecutionCount -= 1
            executingKeys.remove(identity)
        }
        guard !Task.isCancelled else {
            return .failure(CommandError(title: "Command cancelled"))
        }
        // Invoke the handler in the caller's task so bridge cancellation reaches app code.
        let result = await registration.handler(arguments)
        guard !Task.isCancelled else {
            if case let .success(result) = result {
                result.attachment?.discardOwnedFile()
            }
            return .failure(CommandError(title: "Command cancelled"))
        }
        return result
    }

    func unregister(key: String) -> Bool {
        unregister(key: key, matching: nil)
    }

    private func unregister(key: String, matching expectedRegistration: Registration?) -> Bool {
        let identity = Identity.app(key)
        guard let registration = registrations[identity] else { return false }
        guard expectedRegistration == nil || registration === expectedRegistration else { return false }
        registrations.removeValue(forKey: identity)
        bridge?.unregisterCommand(key: key)
        return true
    }
}
