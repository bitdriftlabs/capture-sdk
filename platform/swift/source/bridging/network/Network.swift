// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A protocol allowing for the implementation of the Bitdrift network layer.
/// Implements a bidirectional HTTP stream which delegates the gRPC framing to
/// the native layer. This means that some gRPC handling happens when sending
/// and receiving headers, but otherwise it is a gRPC agnostic data stream.
///
/// Data received via the stream should be propagated to the native side via
/// capture_api_received_data, and capture_api_stream_closed should be called to signal
/// stream closure. Also note that capture_api_release_stream *must* be called with
/// the streamId when Swift is done with the stream to avoid leaking memory.
@objc
public protocol Network {
    ///
    /// Called when the Bitdrift library intends to initialize a new stream.
    ///
    /// - parameter streamId: An opaque id that identifies the new stream.
    /// - parameter headers:  Headers to add to network request.
    ///
    /// - returns: handle to the new stream
    func startStream(_ streamId: UInt, headers: [String: String]) -> NetworkStream
}
