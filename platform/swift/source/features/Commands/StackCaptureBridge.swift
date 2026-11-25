// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

protocol StackSnapshotBridging: StackCaptureCancelling {
    func capture(bufferSizeKb: UInt32, to url: URL) -> Result<StackSnapshotSummary, CommandError>
    func cancel()
}

struct StackSnapshotSummary: Sendable {
    let capturedThreads: Int
    let failedThreads: Int
    let truncatedThreads: Int
    let skippedThreads: Int
    let noUserContextThreads: Int

    var context: [String: String] {
        [
            "capture": "snapshot", "unwinder": "arm64_frame_pointer",
            "captured_threads": String(capturedThreads),
            "failed_threads": String(failedThreads),
            "truncated_threads": String(truncatedThreads),
            "skipped_threads": String(skippedThreads),
            "no_user_context_threads": String(noUserContextThreads),
        ]
    }
}

// Capture and cancellation retain this owner. The cancellation handle is released only after
// both operations finish. Main-thread identity is shared across requests and read at export time.
final class StackCaptureBridge: StackSnapshotBridging, StackProfileBridging, @unchecked Sendable {
    private let pointer = capture_stack_snapshot_bridge_create()

    // Schedule registration at SDK startup without waiting for the main queue. Rust reads the
    // shared identity after capture, so requests created before registration can still use it.
    // If the main queue has not run yet, export leaves its identity unknown.
    static func prepareMainThreadID() {
        if Thread.isMainThread {
            recordMainThreadID()
        } else {
            DispatchQueue.main.async { recordMainThreadID() }
        }
    }

    private static func recordMainThreadID() {
        var id: UInt64 = 0
        if pthread_threadid_np(nil, &id) == 0 {
            capture_stack_set_main_thread_id(id)
        }
    }

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
            skippedThreads: summary.skipped_threads,
            noUserContextThreads: summary.no_user_context_threads
        ))
    }

    func profile(frequencyHz: UInt32, durationMs: UInt32, bufferSizeKb: UInt32, to url: URL)
    -> Result<StackProfileSummary, CommandError> {
        var summary = capture_stack_profile_summary()
        let status = url.path.withCString {
            capture_stack_profile(frequencyHz, durationMs, bufferSizeKb, $0, pointer, &summary)
        }
        guard status == 0 else {
            return .failure(Self.error(status))
        }
        return .success(StackProfileSummary(native: summary))
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
            CommandError.invalidArguments(message: "invalid capture parameters")
        case 2:
            CommandError.alreadyExecuting
        case 3:
            CommandError(message: "unsupported", context: ["description": "stack capture requires ARM64"])
        case 4:
            CommandError(message: "capture_failed")
        case 6:
            CommandError(message: "attachment_too_large", context: ["description": "stack capture JSON exceeds 5 MiB"])
        case 7:
            CommandError(message: "cancelled")
        case 8:
            CommandError(message: "no_useful_stacks")
        default:
            CommandError(message: "attachment_write_failed")
        }
    }
}
