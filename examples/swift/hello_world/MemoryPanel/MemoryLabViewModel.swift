// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Darwin
import SwiftUI

private let kBytesPerKB: UInt64 = 1_024
private let kBytesPerMB: UInt64 = 1_024 * 1_024

final class MemoryLabViewModel: ObservableObject {
    @Published private(set) var usedMB: UInt64 = 0
    @Published private(set) var limitMB: UInt64 = 0
    @Published private(set) var usagePercent: Double = 0

    private var timer: Timer?

    var usagePercentFormatted: String {
        String(format: "%.0f%%", self.usagePercent)
    }

    var gaugeColor: Color {
        switch self.usagePercent {
        case ..<70:
            return Theme.primary
        case 70..<85:
            return Theme.warning
        default:
            return Theme.danger
        }
    }

    func startMonitoring() {
        self.updateMemoryStats()
        self.timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.updateMemoryStats()
        }
    }

    func stopMonitoring() {
        self.timer?.invalidate()
        self.timer = nil
    }

    private func updateMemoryStats() {
        var taskInfo = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info>.stride / MemoryLayout<integer_t>.stride
        )
        let result: kern_return_t = withUnsafeMutablePointer(to: &taskInfo) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: 1) { taskInfoOut in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), taskInfoOut, &count)
            }
        }

        guard result == KERN_SUCCESS else { return }

        let usedKB = taskInfo.phys_footprint / kBytesPerKB
        let availableBytes = os_proc_available_memory()
        let limitKB: UInt64
        if availableBytes > 0 {
            limitKB = usedKB + UInt64(availableBytes) / kBytesPerKB
        } else {
            // os_proc_available_memory() is unavailable in the simulator; fall back to device total RAM.
            limitKB = ProcessInfo.processInfo.physicalMemory / kBytesPerKB
        }

        DispatchQueue.main.async {
            self.usedMB = usedKB / 1_024
            self.limitMB = limitKB / 1_024
            self.usagePercent = limitKB > 0 ? Double(usedKB) / Double(limitKB) * 100 : 0
        }
    }
}

