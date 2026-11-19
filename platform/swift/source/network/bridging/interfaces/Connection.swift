// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import Foundation

typealias NetworkStreamConnection = Connection & NetworkStream

/// An object that represents a bidirectional stream to a remote server.
@objc
protocol Connection {
    /// Starts the connection
    func connect()

    /// Ends the connection and closes their associated streams. This can be called multiple times.
    func end()

    /// Opens the OutputStream and provides the InputStream to the closure.
    ///
    /// - parameter closure: Called with an input stream as its argument after the output stream
    ///                      is opened. Called with `nil` for cases when the output stream
    ///                      was already opened.
    func openAndProvideStream(_ closure: (InputStream?) -> Void)

    /// A handler which will receive updates about when the OutputStream is ready, as well as events coming
    /// from the network.
    var handler: ConnectionDataHandler { get }
}
