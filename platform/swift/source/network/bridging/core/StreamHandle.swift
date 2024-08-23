// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
import Foundation

/// Wrapper around the opaque streamId used to interact with the per-stream native state.
/// To ensure safe memory access, all usages of the streamId are done within this class,
/// allowing us to call the cleanup function once we know that the stream ID is no longer
/// going to be referenced (at deinit).
final class StreamHandle {
    private let streamID: UInt

    init(streamID: UInt) {
        self.streamID = streamID
    }

    deinit {
        capture_api_release_stream(self.streamID)
    }
}

// MARK: - ConnectionDelegate

extension StreamHandle: ConnectionDataHandler {
    func onMessage(_ data: Data) {
        data.withUnsafeBytes { pointer in
            guard let baseAddress = pointer.baseAddress else {
                return
            }

            capture_api_received_data(self.streamID,
                                      baseAddress.assumingMemoryBound(to: UInt8.self),
                                      pointer.count)
        }
    }

    func onComplete(_ error: Error?) {
        let reason: String
        if let error {
            reason = "onComplete with error: \(error)"
        } else {
            reason = "onComplete"
        }

        capture_api_stream_closed(self.streamID, reason)
    }
}
