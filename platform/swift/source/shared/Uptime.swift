// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct Uptime {
    private let uptime: TimeInterval

    /// Returns a high-resolution measurement of system uptime, that continues ticking through device sleep
    /// *and* user- or system-generated clock adjustments. This allows for stable differences to be calculated
    /// between timestamps.
    ///
    /// - returns: Expressed in seconds measurement of system uptime.
    static func get() -> TimeInterval {
        var mib: [Int32] = [CTL_KERN, KERN_BOOTTIME]
        var size: size_t = MemoryLayout<timeval>.stride

        var now = timeval()
        let systemTimeError = gettimeofday(&now, nil) != 0
        assert(!systemTimeError, "timing: system time unavailable")

        var bootTime = timeval()
        let bootTimeError = sysctl(&mib, 2, &bootTime, &size, nil, 0) != 0 || bootTime.tv_sec == 0
        assert(!bootTimeError, "timing: kernel boot time unavailable")

        let seconds = Double(now.tv_sec - bootTime.tv_sec)
        assert(seconds >= 0, "timing: system time precedes boot time")

        let microseconds = Double(now.tv_usec - bootTime.tv_usec)

        return seconds + microseconds / 1_000_000
    }

    /// Captures the current uptime.
    ///
    /// - parameter uptime: The uptime.
    init(uptime: TimeInterval = Uptime.get()) {
        self.uptime = uptime
    }

    /// Calculates the duration of time that passed between now and the provided timing.
    ///
    /// - parameter uptime: The timing to calculate the duration of time from.
    ///
    /// - returns: The duration of time.
    func timeIntervalSince(_ uptime: Uptime) -> TimeInterval {
        return self.uptime - uptime.uptime
    }
}
