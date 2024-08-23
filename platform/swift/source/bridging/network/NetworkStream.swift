// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Interface for a stream that can accept new data to be sent.
///
/// Data received via the stream should be propagated to the native side via
/// capture_api_received_data, and capture_api_stream_closed should be called to signal
/// stream closure. Also note that capture_api_release_stream *must* be called with
/// the streamId when Swift is done with the stream to avoid leaking memory.
@objc
public protocol NetworkStream: AnyObject {
    ///
    /// Called when the Bitdrift library wants to send data over the active stream.
    ///
    /// - parameter baseAddress: Memory address of the data.
    /// - parameter count:       Number of bytes of data to send over the stream.
    ///
    /// - returns: The number of bytes successfully written to the stream. Returns -1 if no bytes were
    ///            written.
    func sendData(_ baseAddress: UnsafePointer<UInt8>, count: Int) -> Int

    /// Called when the Bitdrift library wants terminate the stream.
    func shutdown()
}
