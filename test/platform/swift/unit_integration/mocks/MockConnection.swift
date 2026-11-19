// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CapturePassable
import Foundation

public final class MockConnection: NSObject, Connection, NetworkStream {
    // MARK: - Connection

    var onConnect: (() -> Void)?
    var onOpenAndProvideStream: (() -> Void)?
    var onEnd: (() -> Void)?

    public func connect() {
        self.onConnect?()
    }

    public func openAndProvideStream(_: (InputStream?) -> Void) {
        self.onOpenAndProvideStream?()
    }

    public func end() {
        self.onEnd?()
    }

    // MARK: - NetworkStream

    public var handler: ConnectionDataHandler = StreamHandle(streamID: 0)
    public var onSendData: ((UnsafePointer<UInt8>, Int) -> Void)?

    public func sendData(_ baseAddress: UnsafePointer<UInt8>, count: Int) -> Int {
        self.onSendData?(baseAddress, count)
        return count
    }

    public func shutdown() {}
}
