// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A configuration used to configure Capture session replay feature.
public struct SessionReplayConfiguration {
    /// The number of seconds between consecutive screen replay captures.
    public var captureIntervalSeconds: TimeInterval
    /// The closure called at just before session replay starts. Passed `Replay` object can be used to
    /// configure the session replay further.
    public var willStart: ((Replay) -> Void)?

    /// Initializes a new session replay configuration.
    ///
    /// - parameter captureIntervalSeconds: The number of seconds between consecutive screen replay captures.
    ///                                     The default is 3s.
    /// - parameter willStart:              The closure called at just before session replay starts. Passed
    ///                                     `Replay` object can be used to configure the session replay
    ///                                     further. Passing `nil` means no further configuration.
    ///                                     The default is `nil`.
    public init(captureIntervalSeconds: TimeInterval = 3, willStart: ((Replay) -> Void)? = nil) {
        self.captureIntervalSeconds = captureIntervalSeconds
        self.willStart = willStart
    }
}
