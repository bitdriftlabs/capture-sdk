// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Snapshot of the previous app run status.
public struct PreviousRunInfo: Equatable {
    /// Whether the previous run ended in a fatal termination.
    public let hasFatallyTerminated: Bool

    public init(hasFatallyTerminated: Bool) {
        self.hasFatallyTerminated = hasFatallyTerminated
    }
}
