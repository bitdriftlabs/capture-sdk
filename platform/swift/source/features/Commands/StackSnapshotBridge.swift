// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

protocol StackSnapshotBridging: AnyObject, Sendable {
    func capture(bufferSizeKb: UInt32, to url: URL) -> Result<StackSnapshotSummary, CommandError>
    func cancel()
}

struct StackSnapshotSummary: Sendable {
    let capturedThreads: Int
    let failedThreads: Int
    let truncatedThreads: Int
    let skippedThreads: Int

    var context: [String: String] {
        [
            "capture": "snapshot", "unwinder": "arm64_frame_pointer",
            "captured_threads": String(capturedThreads),
            "failed_threads": String(failedThreads),
            "truncated_threads": String(truncatedThreads),
            "skipped_threads": String(skippedThreads),
        ]
    }
}

// Capture and cancellation retain this owner. The handle contains only an atomic cancellation
// flag, which Rust can read concurrently; it is released after both operations finish.
final class StackSnapshotBridge: StackSnapshotBridging, @unchecked Sendable {
    private let pointer = capture_stack_snapshot_bridge_create()

    func capture(bufferSizeKb: UInt32, to url: URL) -> Result<StackSnapshotSummary, CommandError> {
        var summary = capture_stack_snapshot_summary()
        let status = url.path.withCString {
            capture_stack_snapshot(bufferSizeKb, $0, pointer, &summary)
        }
        guard status == 0 else {
            return .failure(Self.error(status))
        }
        return .success(StackSnapshotSummary(
            capturedThreads: summary.captured_threads,
            failedThreads: summary.failed_threads,
            truncatedThreads: summary.truncated_threads,
            skippedThreads: summary.skipped_threads
        ))
    }

    func cancel() {
        capture_stack_snapshot_bridge_cancel(pointer)
    }

    deinit {
        capture_stack_snapshot_bridge_release(pointer)
    }

    private static func error(_ status: Int32) -> CommandError {
        switch status {
        case 1:
            CommandError.invalidArguments(description: "invalid capture buffer")
        case 2:
            CommandError.alreadyExecuting
        case 3:
            CommandError(title: "unsupported", description: "stack snapshot requires ARM64")
        case 4:
            CommandError(title: "capture_failed")
        case 6:
            CommandError(title: "attachment_too_large", description: "snapshot JSON exceeds 5 MiB")
        case 7:
            CommandError(title: "cancelled")
        case 8:
            CommandError(title: "no_useful_stacks")
        default:
            CommandError(title: "attachment_write_failed")
        }
    }
}
