// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

protocol StackProfileBridging: StackCaptureCancelling {
    func profile(frequencyHz: UInt32, durationMs: UInt32, bufferSizeKb: UInt32, to url: URL)
    -> Result<StackProfileSummary, CommandError>
}

struct StackProfileSummary: Sendable {
    let context: [String: String]

    init(native: capture_stack_profile_summary) {
        context = [
            "capture": "profile", "unwinder": "arm64_frame_pointer",
            "capture_rounds": String(native.capture_rounds),
            "captured_samples": String(native.captured_samples),
            "failed_samples": String(native.failed_samples),
            "truncated_samples": String(native.truncated_samples),
            "skipped_samples": String(native.skipped_samples),
            "retained_samples": String(native.retained_samples),
            "dropped_samples": String(native.dropped_samples),
            "missed_ticks": String(native.missed_ticks),
            "dropped_images": String(native.dropped_images),
            "failed_image_reads": String(native.failed_image_reads),
        ]
    }
}
