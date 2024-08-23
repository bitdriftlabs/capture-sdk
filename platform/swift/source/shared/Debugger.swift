// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

enum Debugger {
    // The value to override the return value of `isAttached()` method with. `nil` means no override.
    private static var mockedIsAttached: Bool?

    /// Mocks the value returned by `isAttached()` method. Used for tests purposes only.
    /// Allows to test emission of ANR events in a test environment.
    ///
    /// - parameter isAttached: The value to return from `isAttached()` method.
    static func mockIsAttached(_ isAttached: Bool) {
        self.mockedIsAttached = isAttached
    }

    /// Reverts the effect of mocking operations. Used for tests purposes only.
    static func unmock() {
        self.mockedIsAttached = nil
    }

    /// Whether a debugger is attached to current application's process.
    ///
    /// - returns: `true` if a debugger is attached, `false` otherwise.
    static func isAttached() -> Bool {
        #if DEBUG
            return self.mockedIsAttached ?? self.isTraced()
        #else
            return false
        #endif
    }

    private static func isTraced() -> Bool {
        var info = kinfo_proc()
        var size = MemoryLayout.stride(ofValue: info)

        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]

        guard sysctl(&mib, UInt32(mib.count), &info, &size, nil, 0) == 0 else {
            return false
        }

        return (info.kp_proc.p_flag & P_TRACED) != 0
    }
}
