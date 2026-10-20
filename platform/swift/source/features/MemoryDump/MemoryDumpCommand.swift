// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

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

        let flags = Flags(
            heapEnabled: self.runtimeValue(.memoryDumpHeapEnabled),
            demangleOnDevice: self.runtimeValue(.memoryDumpDemangleOnDevice)
        )
        return await withCheckedContinuation { continuation in
            self.queue.async {
                continuation.resume(returning: Self.run(request, flags: flags))
            }
        }
    }

    private struct Flags {
        let heapEnabled: Bool
        let demangleOnDevice: Bool
    }

    private static func run(_ request: MemoryDumpRequest, flags: Flags) -> Result<CommandResult, CommandError> {
        var context: [String: String] = [:]
        var report = MemoryDumpReport()
        var stagesRun: [MemoryDumpRequest.Stage] = []

        context["phys_footprint_before"] = VirtualMemoryCounters.physFootprint().map(String.init)

        for stage in request.stages {
            if stage == .heap, !flags.heapEnabled {
                context["heap"] = "disabled"
                continue
            }

            let started = DispatchTime.now()
            do {
                switch stage {
                case .counters:
                    report.counters = try VirtualMemoryCounters.capture()
                case .regions:
                    report.regions = try RegionWalk.capture(request.regions)
                case .heap:
                    let heap = try HeapCensus.capture(request.heap, demangle: flags.demangleOnDevice)
                    report.heap = heap
                    context["heap_truncated"] = String(heap.truncated)
                    context["heap_zones_not_seen"] = heap.zonesNotSeen.joined(separator: ",")
                    context["heap_zones_skipped"] = heap.zonesSkipped.joined(separator: ",")
                }
            } catch {
                return .failure(error)
            }
            context["\(stage.rawValue)_ms"] = Self.milliseconds(since: started)
            stagesRun.append(stage)
        }

        context["phys_footprint_after"] = VirtualMemoryCounters.physFootprint().map(String.init)
        context["stages"] = stagesRun.map(\.rawValue).joined(separator: ",")

        do {
            let attachment = CommandAttachment(data: try report.encoded(), mimeType: "application/json")
            return .success(CommandResult(attachment: attachment, context: context))
        } catch {
            return .failure(CommandError(
                title: "Unable to encode memory dump",
                description: error.localizedDescription
            ))
        }
    }

    private static func milliseconds(since start: DispatchTime) -> String {
        let elapsed = DispatchTime.now().uptimeNanoseconds - start.uptimeNanoseconds
        return String(format: "%.1f", Double(elapsed) / 1_000_000)
    }

    private func runtimeValue(_ variable: RuntimeVariable<Bool>) -> Bool {
        self.logger?.runtimeValue(variable) ?? variable.defaultValue
    }
}
