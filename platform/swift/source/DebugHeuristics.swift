// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

public struct DebugHeuristics {
    /// Simulator check (runtime), we could use `targetEnvironment` too but chose the runtime path.
    public static var isSimulator: Bool {
        ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != nil
    }

    /// TTY attached (Xcode run console). Not bullet-proof, but cheap.
    public static var hasInteractiveTTY: Bool {
        isatty(STDERR_FILENO) == 1 || isatty(STDOUT_FILENO) == 1
    }

    /// Is the running environment likely to be a debug-like environment? Such as
    /// a simulator or an Xcode run console.
    public static var isDebugLikeEnvironment: Bool {
        return isSimulator || hasInteractiveTTY
    }
}
