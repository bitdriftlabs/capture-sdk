// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class MemoryDumpCommand: @unchecked Sendable {
    static let key = "ios_memory_dump"

    /// A lower priority queue increases the time the heap census holds the malloc zone locks,
    /// so dont change this without performing benchmarks.
    private let queue = DispatchQueue(label: "io.bitdrift.capture.memory-dump", qos: .userInitiated)
    private weak var logger: CoreLogging?

    init(logger: CoreLogging) {
        self.logger = logger
    }

    var command: Command {
        Command(key: Self.key) { [self] arguments in
            await self.execute(arguments)
        }
    }

    func execute(_ arguments: CommandArguments) async -> Result<CommandResult, CommandError> {
        guard self.runtimeValue(.memoryDumpEnabled) else {
            return .failure(CommandError(
                title: "Memory dump is disabled",
                description: "Enable \(RuntimeVariable<Bool>.memoryDumpEnabled.name) to run \(Self.key)."
            ))
        }

        let request: MemoryDumpRequest
        do {
            request = try MemoryDumpRequest(arguments: arguments)
        } catch {
            return .failure(error)
        }

        let heapEnabled = self.runtimeValue(.memoryDumpHeapEnabled)
        return await withCheckedContinuation { continuation in
            self.queue.async {
                continuation.resume(returning: Self.run(request, heapEnabled: heapEnabled))
            }
        }
    }

    private static func run(_ request: MemoryDumpRequest, heapEnabled: Bool) -> Result<CommandResult, CommandError> {
        var stages = request.stages
        var context: [String: String] = [:]
        if !heapEnabled, stages.contains(.heap) {
            stages.removeAll { $0 == .heap }
            context["heap"] = "disabled"
        }

        let output = capture_memory_dump(
            stages.reduce(0) { $0 | $1.mask },
            request.heap.budgetMs,
            request.heap.owners
        ) as? [String: Any]
        guard let output else {
            return .failure(CommandError(title: "Unable to capture memory dump"))
        }
        if let error = output["error"] as? String {
            return .failure(CommandError(title: "Unable to capture memory dump", description: error))
        }
        guard let attachment = output["attachment"] as? Data else {
            return .failure(CommandError(title: "Unable to capture memory dump"))
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
