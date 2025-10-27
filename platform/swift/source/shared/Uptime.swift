// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// High resolution wall clock that continues ticking through device sleep and is unaffected by clock adjustments.
struct Uptime {
    private static let timebase: mach_timebase_info_data_t = getTimebase()

    private static func getTimebase() -> mach_timebase_info_data_t {
        var timebase: mach_timebase_info_data_t = mach_timebase_info_data_t()
        let timebaseError = mach_timebase_info(&timebase) != KERN_SUCCESS
        assert(!timebaseError, "Uptime: could not get timebase")
        return timebase
    }

    private let startTime: UInt64 = mach_continuous_time()

    private func ticksToSeconds(_ ticks: UInt64) -> TimeInterval {
        let elapsedNanos = ticks * UInt64(Uptime.timebase.numer) / UInt64(Uptime.timebase.denom)
        return TimeInterval(elapsedNanos) / 1_000_000_000.0
    }

    /// Calculates the duration of time that passed between now and the provided timing.
    ///
    /// - parameter uptime: The timing to calculate the duration of time from.
    ///
    /// - returns: The duration of time.
    func timeIntervalSince(_ uptime: Uptime) -> TimeInterval {
        return ticksToSeconds(mach_continuous_time() - uptime.startTime)
    }
}
