// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Darwin
import Foundation

struct MemoryDump: Encodable {
    let physFootprint: UInt64
    let ledgerPhysFootprintPeak: Int64
    let internalMemory: UInt64
    let compressed: UInt64
    let external: UInt64
    let ledgerTagGraphicsFootprint: Int64
    let ledgerTagGraphicsFootprintCompressed: Int64
    let ledgerTagMediaFootprint: Int64
    let ledgerTagMediaFootprintCompressed: Int64
    let ledgerTagNetworkNonvolatile: Int64
    let ledgerTagNetworkNonvolatileCompressed: Int64

    enum CodingKeys: String, CodingKey {
        case physFootprint = "phys_footprint"
        case ledgerPhysFootprintPeak = "ledger_phys_footprint_peak"
        case internalMemory = "internal"
        case compressed
        case external
        case ledgerTagGraphicsFootprint = "ledger_tag_graphics_footprint"
        case ledgerTagGraphicsFootprintCompressed = "ledger_tag_graphics_footprint_compressed"
        case ledgerTagMediaFootprint = "ledger_tag_media_footprint"
        case ledgerTagMediaFootprintCompressed = "ledger_tag_media_footprint_compressed"
        case ledgerTagNetworkNonvolatile = "ledger_tag_network_nonvolatile"
        case ledgerTagNetworkNonvolatileCompressed = "ledger_tag_network_nonvolatile_compressed"
    }

    static func capture() -> Result<MemoryDump, CommandError> {
        var taskInfo = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info>.stride / MemoryLayout<integer_t>.stride
        )
        let status = withUnsafeMutablePointer(to: &taskInfo) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: 1) { taskInfoOut in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), taskInfoOut, &count)
            }
        }

        guard status == KERN_SUCCESS else {
            return .failure(
                CommandError(
                    title: "Unable to collect memory information",
                    description: "task_info returned \(status)"
                )
            )
        }

        return .success(
            MemoryDump(
                physFootprint: taskInfo.phys_footprint,
                ledgerPhysFootprintPeak: taskInfo.ledger_phys_footprint_peak,
                internalMemory: taskInfo.internal,
                compressed: taskInfo.compressed,
                external: taskInfo.external,
                ledgerTagGraphicsFootprint: taskInfo.ledger_tag_graphics_footprint,
                ledgerTagGraphicsFootprintCompressed: taskInfo.ledger_tag_graphics_footprint_compressed,
                ledgerTagMediaFootprint: taskInfo.ledger_tag_media_footprint,
                ledgerTagMediaFootprintCompressed: taskInfo.ledger_tag_media_footprint_compressed,
                ledgerTagNetworkNonvolatile: taskInfo.ledger_tag_network_nonvolatile,
                ledgerTagNetworkNonvolatileCompressed: taskInfo.ledger_tag_network_nonvolatile_compressed
            )
        )
    }

    func commandResult(isAttachment: Bool) -> Result<CommandResult, CommandError> {
        if !isAttachment {
            return .success(CommandResult(context: self.context))
        }

        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(self)
            return .success(
                CommandResult(
                    attachment: CommandAttachment(
                        data: data,
                        filename: "memory_dump.json",
                        mimeType: "application/json"
                    )
                )
            )
        } catch {
            return .failure(
                CommandError(
                    title: "Unable to encode memory information",
                    description: error.localizedDescription
                )
            )
        }
    }

    private var context: [String: String] {
        [
            "phys_footprint": String(self.physFootprint),
            "ledger_phys_footprint_peak": String(self.ledgerPhysFootprintPeak),
            "internal": String(self.internalMemory),
            "compressed": String(self.compressed),
            "external": String(self.external),
            "ledger_tag_graphics_footprint": String(self.ledgerTagGraphicsFootprint),
            "ledger_tag_graphics_footprint_compressed": String(self.ledgerTagGraphicsFootprintCompressed),
            "ledger_tag_media_footprint": String(self.ledgerTagMediaFootprint),
            "ledger_tag_media_footprint_compressed": String(self.ledgerTagMediaFootprintCompressed),
            "ledger_tag_network_nonvolatile": String(self.ledgerTagNetworkNonvolatile),
            "ledger_tag_network_nonvolatile_compressed": String(self.ledgerTagNetworkNonvolatileCompressed),
        ]
    }
}
