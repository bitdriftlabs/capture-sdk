// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CapturePassable
import Foundation

/// Maximum size (in bytes) that may be queued at once on a Foundation stream buffer.
let kMaxBufferSize: Int = 1_000_000 // 1Mb

/// The idle timeout to use, which closes the stream if we receive no data from the backend for this
/// duration. When accompanied with ping keep alive, this ensures that we'll close the stream
/// after this duration after we stop received pong responses from the server (indicating that the
/// connection is bad)
/// Set to two minutes as the default ping interval is 60s (configured server side).
/// TODO(snowp): Configure this via runtime.
let kIdleTimeout: TimeInterval = 2 * 60

/// Interface defining a Bitdrift networking client.
protocol NetworkClient: AnyObject {
    /// Sets up a connection with the provided handler.
    ///
    /// - parameter url:     URL of the stream (i.e., `https://api.Bitdrift.com/pb.api.foo.Bar`).
    /// - parameter handler: The connection handler to use to handle connection events such as
    ///                      incoming data or connection completion.
    /// - parameter headers: The headers to send with the stream.
    ///
    /// - returns: A connection object that can be used for creating a network stream.
    func createConnection(
        to url: URL,
        handler: ConnectionDataHandler,
        headers: [String: String]
    ) -> NetworkStreamConnection
}
