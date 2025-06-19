// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A configuration representing the feature set enabled for Capture.
public struct Configuration {
    /// The session replay configuration.
    public var sessionReplayConfiguration: SessionReplayConfiguration

    /// .active if Capture should initialize in minimal activity mode
    public var sleepMode: SleepMode

    /// Initializes a new instance of the Capture configuration.
    ///
    /// - parameter sessionReplayConfiguration: The session replay configuration to use.
    /// - parameter sleepMode:                  .active if Capture should initialize in minimal activity mode
    public init(
        sessionReplayConfiguration: SessionReplayConfiguration = .init(),
        sleepMode: SleepMode = .inactive
    ) {
        self.sessionReplayConfiguration = sessionReplayConfiguration
        self.sleepMode = sleepMode
    }
}
