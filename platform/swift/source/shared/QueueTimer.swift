// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The timer that runs on a specified `DispatchQueue`.
final class QueueTimer {
    private let timeInterval: TimeInterval
    private let queue: DispatchQueue
    private let block: () -> Void

    private var dispatchSource: DispatchSourceTimer?

    private init(timeInternval: TimeInterval, queue: DispatchQueue, block: @escaping () -> Void) {
        self.timeInterval = timeInternval
        self.queue = queue
        self.block = block
    }

    /// Schedules a new timer on a specified queue. The initial trigger of a timer happens immediately upon
    /// scheduling it.
    ///
    /// - parameter timeInterval: The number of seconds between firings of the timer. The value has to be
    ///                           greater than 0.
    /// - parameter queue:        The queue to use for running the timer. Passed `block` is executed on this
    ///                           queue.
    /// - parameter block:        The closure to run when the timer fires.
    ///
    /// - returns: The scheduled timer.
    static func scheduledTimer(
        withTimeInterval timeInterval: TimeInterval,
        queue: DispatchQueue,
        block: @escaping () -> Void
    ) -> QueueTimer {
        precondition(timeInterval > 0, "Timer's time interval needs to be greater than 0")

        let timer = QueueTimer(timeInternval: timeInterval, queue: queue, block: block)
        timer.activate()
        return timer
    }

    private func activate() {
        if self.dispatchSource != nil {
            self.invalidate()
        }

        let timerSource = DispatchSource.makeTimerSource(queue: self.queue)
        timerSource.schedule(deadline: .now(), repeating: self.timeInterval)
        timerSource.setEventHandler { [weak self] in
            self?.block()
        }

        timerSource.activate()
        self.dispatchSource = timerSource
    }

    func invalidate() {
        self.dispatchSource?.cancel()
        self.dispatchSource = nil
    }

    deinit {
        self.invalidate()
    }
}
