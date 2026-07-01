// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

struct PreviousRunResolver {
    /// Boot time is captured in microseconds. Two launches within the same boot session can read
    /// values that differ slightly because the kernel derives boot time as `now - uptime` and may
    /// adjust it. A tolerance avoids misreading those sub-second adjustments as a reboot.
    private static let bootTimeToleranceMicroseconds: UInt64 = 1_000_000

    func resolve(
        previousState: PreviousRunStoredState?,
        currentState: PreviousRunCurrentState,
        didCrashLastLaunch: Bool
    ) -> PreviousRunInfo
    {
        guard let previousState else {
            return .unknown
        }
        // In-process crash reports win over the other signals. They are concrete crash
        // records and remain valid across other signal like app/OS updates.
        if didCrashLastLaunch {
            return PreviousRunInfo(terminationReason: .fatalCrash)
        }

        // A clean exit marker means the previous run terminated gracefully (received willTerminate).
        if previousState.wasCleanExit {
            return PreviousRunInfo(terminationReason: .cleanExit)
        }

        // Both appVersion and binaryUUID are checked because the version string alone is not enough:
        // adhoc/development builds often share the same version string across binary changes.
        if previousState.appVersion != currentState.appVersion ||
            previousState.binaryUUID != currentState.binaryUUID {
            return PreviousRunInfo(terminationReason: .appUpdate)
        }

        if previousState.osVersion != currentState.osVersion {
            return PreviousRunInfo(terminationReason: .osUpdate)
        }

        if previousState.bootTime != 0,
           currentState.bootTime != 0,
           Self.bootTimeDelta(previousState.bootTime, currentState.bootTime) > Self.bootTimeToleranceMicroseconds {
            // The device rebooted between runs while the previous run ended without a clean-exit
            // marker or captured crash. This currently collapses to `.unknown`, same as the
            // fallthrough below.
            // TODO: introduce a dedicated status (e.g. forced reboot / power-off) if we want to
            // distinguish a reboot-induced termination from a genuinely unknown one.
            return .unknown
        }

        // Checked last since it's the least specific signal we have: only reported in debug builds,
        // for a launch that otherwise looked unclean but wasn't explained by a crash, reboot, or
        // app/OS update.
        if previousState.wasDebuggerAttached {
            return PreviousRunInfo(terminationReason: .debuggerAttached)
        }

        return .unknown
    }

    private static func bootTimeDelta(_ lhs: UInt64, _ rhs: UInt64) -> UInt64 {
        return lhs > rhs ? lhs - rhs : rhs - lhs
    }
}
