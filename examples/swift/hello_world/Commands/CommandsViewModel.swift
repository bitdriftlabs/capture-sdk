// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import SwiftUI

@MainActor
final class CommandsViewModel: ObservableObject {
    let commands = [
        CommandDefinition(
            key: "memory_dump",
            description: "Returns TASK_VM_INFO metrics; use is_attachment for a JSON attachment.",
            isBuiltIn: false
        ),
        CommandDefinition(key: "flip_flag", description: "Toggles a named flag.", isBuiltIn: false),
        CommandDefinition(
            key: "screenshot",
            description: "Takes a screenshot of the app.",
            isBuiltIn: true
        ),
    ]

    @Published private(set) var registeredKeys = Set<String>()
    @Published private(set) var invocationsByKey: [String: [CommandInvocationRecord]] = [:]
    @Published private(set) var registrationErrors: [String: String] = [:]

    private let store = CommandsStore()
    private var handles: [String: CommandHandle] = [:]

    var customCommands: [CommandDefinition] {
        self.commands.filter { !$0.isBuiltIn }
    }

    var builtInCommands: [CommandDefinition] {
        self.commands.filter(\.isBuiltIn)
    }

    func isRegistered(_ key: String) -> Bool {
        self.registeredKeys.contains(key)
    }

    func setRegistered(_ shouldRegister: Bool, for key: String) {
        guard let command = self.commands.first(where: { $0.key == key }), !command.isBuiltIn else {
            return
        }
        Task {
            if shouldRegister {
                await self.register(key: key)
            } else {
                await self.unregister(key: key)
            }
        }
    }

    func refreshInvocations(for key: String) {
        Task {
            self.invocationsByKey[key] = await self.store.invocations(for: key)
        }
    }

    private func register(key: String) async {
        guard !self.registeredKeys.contains(key) else { return }

        do {
            let store = self.store
            let handle = try await Logger.registerCommand(key: key) { [weak self] arguments in
                let result = await store.execute(key: key, arguments: arguments)
                await self?.updateInvocations(for: key)
                return result
            }
            self.handles[key] = handle
            self.registeredKeys.insert(key)
            self.registrationErrors[key] = nil
        } catch {
            self.registrationErrors[key] = error.localizedDescription
        }
    }

    private func unregister(key: String) async {
        if let handle = self.handles.removeValue(forKey: key) {
            await handle.unregister()
        } else {
            await Logger.unregisterCommand(key: key)
        }
        self.registeredKeys.remove(key)
        self.registrationErrors[key] = nil
    }

    private func updateInvocations(for key: String) async {
        self.invocationsByKey[key] = await self.store.invocations(for: key)
    }
}
