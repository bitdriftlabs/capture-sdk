// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CapturePassable
import Foundation

final class MockConnection: NSObject, Connection, NetworkStream {
    // MARK: - Connection

    var onConnect: (() -> Void)?
    var onOpenAndProvideStream: (() -> Void)?
    var onEnd: (() -> Void)?

    func connect() {
        self.onConnect?()
    }

    func openAndProvideStream(_: (InputStream?) -> Void) {
        self.onOpenAndProvideStream?()
    }

    func end() {
        self.onEnd?()
    }

    // MARK: - NetworkStream

    var handler: ConnectionDataHandler = StreamHandle(streamID: 0)
    var onSendData: ((UnsafePointer<UInt8>, Int) -> Void)?

    func sendData(_ baseAddress: UnsafePointer<UInt8>, count: Int) -> Int {
        self.onSendData?(baseAddress, count)
        return count
    }

    func shutdown() {}
}
