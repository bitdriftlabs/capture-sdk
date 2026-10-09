// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

actor CommandRegistry {
    private final class Registration: @unchecked Sendable {
        let handler: CommandHandler

        init(handler: @escaping CommandHandler) {
            self.handler = handler
        }
    }

    private let bridge: (any CommandRegistrationBridging)?
    private let maxConcurrentExecutions: Int
    private var registrations = [String: Registration]()
    private var executingKeys = Set<String>()
    private var activeExecutionCount = 0

    init(
        commands: [Command] = [],
        bridge: (any CommandRegistrationBridging)? = nil,
        maxConcurrentExecutions: Int = 10
    ) {
        self.bridge = bridge
        self.maxConcurrentExecutions = maxConcurrentExecutions
        for command in commands {
            registrations[command.key] = Registration(handler: command.handler)
        }
    }

    func register(
        key: String,
        handler: @escaping CommandHandler,
        target: CommandsTarget
    ) -> CommandHandle {
        let registration = Registration(handler: handler)
        if registrations[key] != nil {
            registrations[key] = registration
        } else {
            registrations[key] = registration
            bridge?.registerCommand(key: key, target: target)
        }
        return CommandHandle { [weak self, weak registration] in
            guard let registration else { return }
            await self?.unregister(key: key, matching: registration)
        }
    }

    func execute(key: String, arguments: CommandArguments) async -> Result<CommandResult, CommandError> {
        guard let registration = registrations[key] else {
            return .failure(.notFound)
        }
        guard executingKeys.insert(key).inserted else {
            return .failure(.alreadyExecuting)
        }
        guard activeExecutionCount < maxConcurrentExecutions else {
            executingKeys.remove(key)
            return .failure(.maximumConcurrency)
        }
        activeExecutionCount += 1
        defer {
            activeExecutionCount -= 1
            executingKeys.remove(key)
        }
        return await registration.handler(arguments)
    }

    func unregister(key: String) {
        unregister(key: key, matching: nil)
    }

    private func unregister(key: String, matching expectedRegistration: Registration?) {
        guard let registration = registrations[key] else { return }
        guard expectedRegistration == nil || registration === expectedRegistration else { return }
        registrations.removeValue(forKey: key)
        bridge?.unregisterCommand(key: key)
    }
}
