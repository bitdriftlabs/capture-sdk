// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CapturePassable
import Foundation

@objc
protocol ConnectionDataHandler: AnyObject {
    /// Callback for when a new message has been received by the stream.
    ///
    /// - parameter data: Message received from the stream.
    ///
    func onMessage(_ data: Data)

    /// Callback for when a stream completes.
    ///
    /// - parameter error: Error that occurred when the stream is complete (if any).
    ///
    func onComplete(_ error: Error?)
}
