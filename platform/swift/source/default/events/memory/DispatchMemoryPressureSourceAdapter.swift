// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

protocol MemoryPressureSourceProvider {
    func setEventHandler(handler: @escaping () -> Void)
    func activate()
    func cancel()
    var isCancelled: Bool { get }
    var data: DispatchSource.MemoryPressureEvent { get }
}

final class DispatchMemoryPressureSourceAdapter: MemoryPressureSourceProvider {
    private let source: DispatchSourceMemoryPressure

    init() {
        self.source = DispatchSource.makeMemoryPressureSource(
            eventMask: .all,
            queue: .serial(withLabelSuffix: "DispatchSourceMemoryMonitor", target: .default)
        )
    }

    func setEventHandler(handler: @escaping () -> Void) {
        self.source.setEventHandler(handler: handler)
    }

    func activate() { self.source.activate() }
    func cancel() { self.source.cancel() }

    var isCancelled: Bool { self.source.isCancelled }
    var data: DispatchSource.MemoryPressureEvent { self.source.data }
}
