// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Darwin
import Foundation

/// The kernel's accounting of this process, from a single `TASK_VM_INFO` call.
enum VirtualMemoryCounters {
    struct Report: Encodable, Equatable {
        let physFootprint: UInt64
        let physFootprintPeak: UInt64?
        let `internal`: UInt64
        let compressed: UInt64
        let external: UInt64
        let residentSize: UInt64
        let decompressions: UInt64?
        let limitBytesRemaining: UInt64?
        let availableBeforeKill: UInt64?
        let ledgers: Ledgers?
    }

    /// The kernel's attribution of the footprint by category.
    /// Each value includes its compressed value.
    struct Ledgers: Encodable, Equatable {
        let graphics: UInt64
        let media: UInt64
        let network: UInt64
        let neural: UInt64
        let purgeableVolatile: UInt64
        let purgeableNonvolatile: UInt64
    }

    static func capture() throws(CommandError) -> Report {
        guard let reply = TaskVirtualMemoryInfo.read() else {
            throw CommandError(title: "Unable to collect memory counters", description: "task_info failed")
        }
        let info = reply.info

        var ledgers: Ledgers?
        if reply.has(\.ledger_tag_neural_footprint_compressed) {
            ledgers = Ledgers(
                graphics: sum(info.ledger_tag_graphics_footprint, info.ledger_tag_graphics_footprint_compressed),
                media: sum(info.ledger_tag_media_footprint, info.ledger_tag_media_footprint_compressed),
                network: sum(info.ledger_tag_network_nonvolatile, info.ledger_tag_network_nonvolatile_compressed),
                neural: sum(info.ledger_tag_neural_footprint, info.ledger_tag_neural_footprint_compressed),
                purgeableVolatile: sum(info.ledger_purgeable_volatile, info.ledger_purgeable_volatile_compressed),
                purgeableNonvolatile: sum(
                    info.ledger_purgeable_nonvolatile,
                    info.ledger_purgeable_novolatile_compressed
                )
            )
        }

        let available = UInt64(os_proc_available_memory())

        return Report(
            physFootprint: info.phys_footprint,
            physFootprintPeak: reply.has(\.ledger_phys_footprint_peak)
                ? UInt64(clamping: info.ledger_phys_footprint_peak)
                : nil,
            internal: info.internal,
            compressed: info.compressed,
            external: info.external,
            residentSize: info.resident_size,
            decompressions: reply.has(\.decompressions) ? UInt64(clamping: info.decompressions) : nil,
            limitBytesRemaining: reply.has(\.limit_bytes_remaining) ? info.limit_bytes_remaining : nil,
            availableBeforeKill: available > 0 ? available : nil,
            ledgers: ledgers
        )
    }

    /// Reads the current `phys_footprint`.
    ///
    /// - returns: The footprint in bytes, or `nil` if the kernel refused the call.
    static func physFootprint() -> UInt64? {
        TaskVirtualMemoryInfo.read()?.info.phys_footprint
    }

    private static func sum(_ values: Int64...) -> UInt64 {
        values.reduce(0) { $0 + UInt64(clamping: $1) }
    }
}

/// TODO(ademarco): move this to MemorySnapshotProvider so we have the same logic
/// to get vm info from the mach task.
struct TaskVirtualMemoryInfo {
    let info: task_vm_info_data_t
    let count: mach_msg_type_number_t

    static func read() -> TaskVirtualMemoryInfo? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size
        )
        let status = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        guard status == KERN_SUCCESS else {
            return nil
        }
        return TaskVirtualMemoryInfo(info: info, count: count)
    }

    /// The `TASK_VM_INFO` struct changed across OS releases. They added fields over time.
    /// So a field past the reply is not reported rather than zero.
    ///
    /// - parameter field: The field to check.
    ///
    /// - returns: Whether the kernel's reply reaches `field`.
    func has<Value>(_ field: KeyPath<task_vm_info_data_t, Value>) -> Bool {
        guard let offset = MemoryLayout<task_vm_info_data_t>.offset(of: field) else {
            return false
        }
        return Int(count) * MemoryLayout<natural_t>.size >= offset + MemoryLayout<Value>.size
    }
}
