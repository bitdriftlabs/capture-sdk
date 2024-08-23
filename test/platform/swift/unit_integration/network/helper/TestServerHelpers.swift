// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CaptureTestBridge
import Foundation

// Wraps all of the `await_XXX` functions into completion blocks.
// These calls get dispatched onto a queue to wait, otherwise it'll block the main queue.
// The callbacks then happen on a separate queue as well.
public func nextApiStream() async throws -> Int32 {
    return try await withCheckedThrowingContinuation { continuation in
        next_test_api_stream(ContinuationWrapper(continuation: continuation))
    }
}

public func serverReceivedHandshake(_ streamId: Int32) async throws {
    _ = try await withCheckedThrowingContinuation { continuation in
        test_stream_received_handshake(streamId, ContinuationWrapper(continuation: continuation))
    }
}

public func serverStreamClosed(_ streamId: Int32, _ waitTime: UInt64) async throws {
    _ = try await withCheckedThrowingContinuation { continuation in
        test_stream_closed(streamId, waitTime, ContinuationWrapper(continuation: continuation))
    }
}
