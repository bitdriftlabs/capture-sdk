// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

actor CommandRegistry {
    private enum HandlerAcquisition {
        case handler(CommandHandler)
        case notFound
        case unregistered
    }

    private final class Registration: @unchecked Sendable {
        let handler: CommandHandler

        init(handler: @escaping CommandHandler) {
            self.handler = handler
        }
    }

    private struct Entry {
        var registration: Registration
        var isExecuting = false
        var waiters = [CheckedContinuation<HandlerAcquisition, Never>]()

        init(registration: Registration) {
            self.registration = registration
        }
    }

    private let bridge: (any CommandRegistrationBridging)?
    private var entries = [String: Entry]()

    init(
        commands: [Command] = [],
        bridge: (any CommandRegistrationBridging)? = nil
    ) {
        self.bridge = bridge
        for command in commands {
            entries[command.key] = Entry(registration: Registration(handler: command.handler))
        }
    }

    func register(
        key: String,
        handler: @escaping CommandHandler,
        target: CommandsTarget
    ) -> CommandHandle {
        let registration = Registration(handler: handler)
        if var entry = entries[key] {
            entry.registration = registration
            entries[key] = entry
        } else {
            entries[key] = Entry(registration: registration)
            bridge?.registerCommand(key: key, target: target)
        }
        return CommandHandle { [weak self, weak registration] in
            guard let registration else { return }
            await self?.unregister(key: key, matching: registration)
        }
    }

    func execute(key: String, arguments: CommandArguments) async -> Result<CommandResult, CommandError> {
        let acquisition = await acquireHandler(for: key)
        let handler: CommandHandler
        switch acquisition {
        case let .handler(acquiredHandler):
            handler = acquiredHandler
        case .notFound:
            return .failure(.notFound)
        case .unregistered:
            return .failure(.unregistered)
        }
        let result = await handler(arguments)
        releaseHandler(for: key)
        return result
    }

    func unregister(key: String) {
        unregister(key: key, matching: nil)
    }

    private func unregister(key: String, matching expectedRegistration: Registration?) {
        guard let entry = entries[key] else { return }
        guard expectedRegistration == nil || entry.registration === expectedRegistration else { return }
        entries.removeValue(forKey: key)
        bridge?.unregisterCommand(key: key)
        entry.waiters.forEach { $0.resume(returning: .unregistered) }
    }

    func queuedExecutionCount(for key: String) -> Int {
        entries[key]?.waiters.count ?? 0
    }

    private func acquireHandler(for key: String) async -> HandlerAcquisition {
        guard var entry = entries[key] else { return .notFound }
        guard !entry.isExecuting else {
            return await withCheckedContinuation { continuation in
                entry.waiters.append(continuation)
                entries[key] = entry
            }
        }
        entry.isExecuting = true
        entries[key] = entry
        return .handler(entry.registration.handler)
    }

    private func releaseHandler(for key: String) {
        guard var entry = entries[key] else { return }
        if let waiter = entry.waiters.first {
            entry.waiters.removeFirst()
            entries[key] = entry
            waiter.resume(returning: .handler(entry.registration.handler))
        } else {
            entry.isExecuting = false
            entries[key] = entry
        }
    }
}
