// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation

public class MockMemoryPressureProvider: MemoryPressureSourceProvider {
    public var didCallSetEventHandler = false

    public var eventHandler: (() -> Void)?
    public func setEventHandler(handler: @escaping () -> Void) {
        didCallSetEventHandler = true
        eventHandler = handler
    }

    public var didActivate = false
    public func activate() {
        didActivate = true
    }

    public var didCancel = false
    public func cancel() {
        didCancel = true
    }

    public var isCancelled: Bool = false
    public var data: DispatchSource.MemoryPressureEvent = .all

    public func simulatePressureEvent(_ event: DispatchSource.MemoryPressureEvent) {
        self.data = event
        self.eventHandler?()
    }
}
