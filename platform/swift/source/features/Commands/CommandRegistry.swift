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

    private struct Entry {
        let handler: CommandHandler
        var isExecuting = false
        var waiters = [CheckedContinuation<HandlerAcquisition, Never>]()
    }

    private var entries = [String: Entry]()

    func register(key: String, handler: @escaping CommandHandler) throws -> CommandHandle {
        guard entries[key] == nil else { throw CommandRegistrationError.duplicateKey(key) }
        entries[key] = Entry(handler: handler)
        return CommandHandle { [weak self] in await self?.unregister(key: key) }
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
        guard let entry = entries.removeValue(forKey: key) else { return }
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
        return .handler(entry.handler)
    }

    private func releaseHandler(for key: String) {
        guard var entry = entries[key] else { return }
        if let waiter = entry.waiters.first {
            entry.waiters.removeFirst()
            entries[key] = entry
            waiter.resume(returning: .handler(entry.handler))
        } else {
            entry.isExecuting = false
            entries[key] = entry
        }
    }
}
