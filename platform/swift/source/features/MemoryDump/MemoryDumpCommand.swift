// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class MemoryDumpCommand: @unchecked Sendable {
    static let type = BuiltinCommandType.memoryDump
    static let artifactType = "memory_dump"
    static let timeoutMilliseconds: Int64 = 15_000
    /// Time the heap census may hold the malloc zone locks.
    static let heapBudgetMs: UInt64 = 250
    /// Credit unnamed heap blocks to the named object pointing at them.
    static let heapOwners = true

    /// A lower priority queue increases the time the heap census holds the malloc zone locks,
    /// so dont change this without performing benchmarks.
    private let queue = DispatchQueue(label: "io.bitdrift.capture.memory-dump", qos: .userInitiated)
    private weak var logger: CoreLogging?
    private let heapBudgetMs: UInt64

    init(logger: CoreLogging, heapBudgetMs: UInt64 = MemoryDumpCommand.heapBudgetMs) {
        self.logger = logger
        self.heapBudgetMs = heapBudgetMs
    }

    var builtin: CommandRegistry.Builtin {
        let command = Command(key: Self.type.key) { [self] arguments in
            await self.execute(arguments)
        }
        return .init(
            type: Self.type.rawValue,
            command: command,
            artifactType: Self.artifactType,
            timeoutMilliseconds: Self.timeoutMilliseconds
        )
    }

    func execute(_ arguments: CommandArguments) async -> Result<CommandResult, CommandError> {
        guard self.runtimeValue(.memoryDumpEnabled) else {
            let flag = RuntimeVariable<Bool>.memoryDumpEnabled.name
            return .failure(CommandError(message: "Memory dump is disabled: Enable \(flag) to run \(Self.type.key)."))
        }

        let request: MemoryDumpRequest
        do {
            request = try MemoryDumpRequest(arguments: arguments)
        } catch {
            return .failure(error)
        }

        let heapEnabled = self.runtimeValue(.memoryDumpHeapEnabled)
        let heapBudgetMs = self.heapBudgetMs
        return await withCheckedContinuation { continuation in
            self.queue.async {
                let result = Self.run(request, heapEnabled: heapEnabled, heapBudgetMs: heapBudgetMs)
                continuation.resume(returning: result)
            }
        }
    }

    private static func run(
        _ request: MemoryDumpRequest,
        heapEnabled: Bool,
        heapBudgetMs: UInt64
    ) -> Result<CommandResult, CommandError> {
        var stages = request.stages
        var context: [String: String] = [:]
        if !heapEnabled, stages.contains(.heap) {
            stages.removeAll { $0 == .heap }
            context["heap"] = "disabled"
        }

        let output = capture_memory_dump(
            stages.reduce(0) { $0 | $1.mask },
            heapBudgetMs,
            Self.heapOwners
        ) as? [String: Any]
        guard let output else {
            return .failure(CommandError(message: "Unable to capture memory dump"))
        }
        if let error = output["error"] as? String {
            let message = error.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "Unable to capture memory dump"
                : "Unable to capture memory dump: \(error)"
            return .failure(CommandError(message: message))
        }
        guard let attachment = output["attachment"] as? Data else {
            return .failure(CommandError(message: "Unable to capture memory dump"))
        }

        context.merge(output["context"] as? [String: String] ?? [:]) { current, _ in current }
        return .success(CommandResult(
            attachment: CommandAttachment(data: attachment, mimeType: "application/vnd.bitdrift.ios-memory-dump"),
            context: context
        ))
    }

    private func runtimeValue(_ variable: RuntimeVariable<Bool>) -> Bool {
        self.logger?.runtimeValue(variable) ?? variable.defaultValue
    }
}

private extension MemoryDumpRequest.Stage {
    /// The stage's bit in `capture_memory_dump`'s mask.
    var mask: UInt32 {
        switch self {
        case .counters:
            1 << 0
        case .regions:
            1 << 1
        case .heap:
            1 << 2
        }
    }
}
