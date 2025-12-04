// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Darwin
import Foundation
import UIKit

private let kBytesPerKB: UInt64 = 1024

final class MemorySnapshotProvider {
    private let deviceTotalMemoryKB = ProcessInfo.processInfo.physicalMemory / kBytesPerKB
    private var sequenceNumber = 0

    var logger: CoreLogging?

    private func getTaskInfo() -> task_vm_info_data_t? {
        var taskInfo = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info>.stride / MemoryLayout<integer_t>.stride)
        let result: kern_return_t = withUnsafeMutablePointer(to: &taskInfo) { pointer in
            return pointer.withMemoryRebound(to: integer_t.self, capacity: 1) { taskInfoOut in
                return task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), taskInfoOut, &count)
            }
        }

        guard result == KERN_SUCCESS else {
            assertionFailure("Expected success from memory usage fetch")
            return nil
        }

        return taskInfo
    }

    private func remainingAvailableMemoryKBForApp(taskInfo: task_vm_info_data_t) -> UInt64 {
        let availableMemory = os_proc_available_memory()
        return availableMemory < 0 ? 0 : UInt64(availableMemory) / kBytesPerKB
    }
}

extension MemorySnapshotProvider: ResourceSnapshotProvider {
    func makeSnapshot() -> ResourceSnapshot? {
        let cfTimeSinceDeviceBoot = CFAbsoluteTimeGetCurrent()
        guard let taskInfo = self.getTaskInfo() else {
            return nil
        }

        let appTotalMemoryUsedKB = taskInfo.phys_footprint / kBytesPerKB
        let lowMemoryConfigThresholdPercent = self.logger?.runtimeValue(.appLowMemoryPercentThreshold)

        self.sequenceNumber += 1
        return MemorySnapshot(
            appTotalMemoryLimitKB:
                appTotalMemoryUsedKB + self.remainingAvailableMemoryKBForApp(taskInfo: taskInfo),
            appTotalMemoryUsedKB: appTotalMemoryUsedKB,
            deviceTotalMemoryKB: self.deviceTotalMemoryKB,
            lowMemoryConfigThresholdPercent: lowMemoryConfigThresholdPercent,
            relativeTimestamp: nil,
            timeSinceDeviceBoot: cfTimeSinceDeviceBoot,
            sequenceNumber: self.sequenceNumber,
            timeToCaptureMicroseconds: Int((CFAbsoluteTimeGetCurrent() - cfTimeSinceDeviceBoot) * 1_000_000)
        )
    }
}
